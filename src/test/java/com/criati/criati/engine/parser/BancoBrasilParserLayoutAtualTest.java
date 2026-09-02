package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Layout ATUAL do "Consultas - Investimentos Fundos - Mensal" do BB, em que
 * os rotulos do "Resumo do mes" vem em caixa mista ("Aplicações (+)") e nao
 * mais em caixa alta ("APLICAÇÕES (+)"), que e o que
 * {@link BancoBrasilParserTest} prende.
 *
 * As duas fixtures existem lado a lado de proposito: a correcao que fez o
 * layout atual passar nao pode derrubar o anterior, porque nao ha garantia de
 * que o BB tenha migrado todos os extratos de uma vez.
 *
 * Motivo desta classe: os regex do parser eram compilados com
 * Pattern.CASE_INSENSITIVE sem Pattern.UNICODE_CASE, e essa flag sozinha so
 * dobra caixa em US-ASCII. "APLICAÇÕES" e o unico rotulo do resumo com letras
 * fora do ASCII (Ç e Õ), entao era o unico campo que deixava de casar quando o
 * BB passou a imprimir "Aplicações (+)" — o extrato chegava completo e o
 * parser devolvia aplicacoes = null.
 *
 * Fixture: derivada de dois documentos reais (competencia 08/2026), com a
 * estrutura, a caixa e as quebras de linha preservadas exatamente como o
 * PDFBox as entrega. Conta, agencia, nome do cotista, identificador da
 * transacao, nome do atendente e todos os valores monetarios sao ficticios —
 * este repositorio e publico. Nomes e CNPJs dos fundos sao dado publico e
 * ficaram como no original, no mesmo criterio ja adotado na outra fixture.
 *
 * Um detalhe da fixture e proposital: no documento real a quebra de pagina
 * cai DENTRO do "Resumo do mes" do terceiro fundo, entre "Rendimento Bruto
 * (+)" e "Imposto de Renda (-)". O PDFBox concatena as paginas sem deixar
 * marcador, entao o resumo partido chega ao parser como texto continuo — e e
 * assim que a fixture o reproduz.
 */
class BancoBrasilParserLayoutAtualTest {

    private static final String NOME_ARQUIVO = "9876-5 C.I.pdf";

    /**
     * Linha do resumo do primeiro fundo. Usada para simular o layout sem o
     * campo, que e coisa diferente de campo com valor zero.
     */
    private static final String LINHA_APLICACOES_RESUMO = "Aplicações (+) 78.763,14";

    private static String texto;

    private final BancoBrasilParser parser = new BancoBrasilParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = BancoBrasilParserLayoutAtualTest.class.getResourceAsStream(
                "/bb-investimentos-fundos-mensal-layout-atual.txt")) {

            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto() {
        return new DocumentoContexto(NOME_ARQUIVO, texto);
    }

    private List<ExtratoInvestimento> processarTodos() {
        return parser.processarTodos(contexto());
    }

    @Test
    @DisplayName("reconhece o layout atual do BB, em caixa mista")
    void suportaLayoutAtual() {
        assertTrue(parser.suporta(contexto()));
    }

    @Test
    @DisplayName("devolve uma posicao por fundo do documento")
    void devolveUmaPosicaoPorFundo() {
        assertEquals(3, processarTodos().size());
    }

    /**
     * A conta compoe a chave da posicao no consumidor (competencia + conta +
     * CNPJ). A agencia aparece na linha ANTERIOR e tem o mesmo formato: e a
     * captura errada mais provavel aqui.
     */
    @Test
    @DisplayName("le a conta sem a agencia e no formato digitos-digito")
    void extraiContaNoFormatoDoContrato() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertEquals("9876-5", extrato.getConta());
        }
    }

    @Test
    @DisplayName("conta e competencia do cabecalho valem para todos os fundos")
    void cabecalhoEhCopiadoParaTodosOsItens() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertEquals("08/2026", extrato.getCompetencia());
            assertEquals("BB", extrato.getInstituicao());
        }
    }

    /**
     * O caso que motivou a correcao: rotulo em caixa mista, com Ç e Õ, e
     * valor diferente de zero. Antes da flag UNICODE_CASE este campo — e so
     * ele — voltava null, e o documento inteiro era reprovado como
     * "incompleto" mesmo estando integro.
     */
    @Test
    @DisplayName("le Aplicações (+) 78.763,14 do resumo em caixa mista")
    void leAplicacoesEmCaixaMistaComValor() {
        ExtratoInvestimento extrato = processarTodos().get(0);

        assertNotNull(extrato.getAplicacoes(),
                "aplicacoes voltou null com o rotulo presente no documento:"
                        + " a dobra de caixa em Ç/Õ provavelmente quebrou de novo");

        assertEquals("78.763,14", extrato.getAplicacoes());
    }

    @Test
    @DisplayName("le todos os campos do primeiro fundo")
    void extraiPrimeiroFundo() {
        ExtratoInvestimento extrato = processarTodos().get(0);

        assertEquals("BB RF Fluxo Sb", extrato.getNomeFundo());
        assertEquals("63.197.387/0001-38", extrato.getCnpjFundo());
        assertEquals("100.000,00", extrato.getSaldoInicial());
        assertEquals("78.763,14", extrato.getAplicacoes());
        assertEquals("20.000,00", extrato.getResgates());
        assertEquals("1.236,86", extrato.getRendimentos());
        assertEquals("160.000,00", extrato.getSaldoFinal());
        assertEquals("1,2500", extrato.getRentabilidadeMes());
        assertEquals("7,5000", extrato.getRentabilidadeAno());
        assertEquals("12,5000", extrato.getRentabilidade12Meses());
    }

    @Test
    @DisplayName("le todos os campos do segundo fundo")
    void extraiSegundoFundo() {
        ExtratoInvestimento extrato = processarTodos().get(1);

        assertEquals("BB Previd CP Fluxo", extrato.getNomeFundo());
        assertEquals("13.077.415/0001-05", extrato.getCnpjFundo());
        assertEquals("200.000,00", extrato.getSaldoInicial());
        assertEquals("0,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
        assertEquals("2.000,00", extrato.getRendimentos());
        assertEquals("202.000,00", extrato.getSaldoFinal());
        assertEquals("1,0000", extrato.getRentabilidadeMes());
        assertEquals("8,0000", extrato.getRentabilidadeAno());
        assertEquals("13,0000", extrato.getRentabilidade12Meses());
    }

    /**
     * No documento real a quebra de pagina cai no meio do resumo deste fundo,
     * entre "Rendimento Bruto (+)" e "Imposto de Renda (-)". Se o recorte dos
     * blocos parasse no fim da pagina, saldoFinal e as rentabilidades — que
     * estao depois do corte — voltariam null.
     */
    @Test
    @DisplayName("le o terceiro fundo inteiro, com o resumo cortado pela quebra de pagina")
    void extraiTerceiroFundoAtravessandoPagina() {
        ExtratoInvestimento extrato = processarTodos().get(2);

        assertEquals("BB Previd RF Perfil", extrato.getNomeFundo());
        assertEquals("13.077.418/0001-49", extrato.getCnpjFundo());
        assertEquals("300.000,00", extrato.getSaldoInicial());
        assertEquals("0,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
        assertEquals("3.000,00", extrato.getRendimentos());
        assertEquals("303.000,00", extrato.getSaldoFinal());
        assertEquals("1,1000", extrato.getRentabilidadeMes());
        assertEquals("9,0000", extrato.getRentabilidadeAno());
        assertEquals("14,0000", extrato.getRentabilidade12Meses());
    }

    /**
     * Zero e valor lido, nao campo faltando. Se o parser tratasse "0,00" como
     * ausencia, todo fundo parado no mes viraria erro de importacao.
     */
    @Test
    @DisplayName("Aplicações (+) 0,00 e valor presente, nunca null")
    void aplicacoesZeroEhValorPresente() {
        List<ExtratoInvestimento> extratos = processarTodos();

        for (int i : new int[] { 1, 2 }) {
            ExtratoInvestimento extrato = extratos.get(i);

            assertNotNull(extrato.getAplicacoes(),
                    "aplicacoes 0,00 voltou null em " + extrato.getNomeFundo()
                            + ": zero virou campo ausente");

            assertEquals("0,00", extrato.getAplicacoes());
            assertDoesNotThrow(() -> parser.validar(extrato, contexto()));
        }
    }

    @Test
    @DisplayName("os tres fundos nao compartilham valores")
    void fundosNaoSeMisturam() {
        List<ExtratoInvestimento> extratos = processarTodos();

        assertEquals(3, extratos.stream()
                .map(ExtratoInvestimento::getSaldoFinal)
                .distinct()
                .count(),
                "dois fundos sairam com o mesmo saldo final: o recorte dos"
                        + " blocos por fundo provavelmente quebrou");

        assertEquals(3, extratos.stream()
                .map(ExtratoInvestimento::getCnpjFundo)
                .distinct()
                .count(),
                "dois fundos sairam com o mesmo CNPJ: o recorte dos blocos"
                        + " por fundo provavelmente quebrou");
    }

    @Test
    @DisplayName("documento completo passa na validacao dos tres fundos")
    void validarAceitaDocumentoCompleto() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertDoesNotThrow(() -> parser.validar(extrato, contexto()));
        }
    }

    /**
     * O contrario do bug: quando o rotulo REALMENTE nao esta no documento, o
     * campo continua null e a posicao continua reprovada. A correcao afrouxou
     * a dobra de caixa, nao a exigencia do campo.
     */
    @Test
    @DisplayName("ausencia real de Aplicações (+) continua reprovando o fundo")
    void aplicacoesRealmenteAusenteContinuaInvalido() {
        DocumentoContexto semAplicacoes = new DocumentoContexto(
                NOME_ARQUIVO, texto.replace(LINHA_APLICACOES_RESUMO, ""));

        ExtratoInvestimento extrato = parser.processarTodos(semAplicacoes).get(0);

        assertNull(extrato.getAplicacoes());

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(extrato, semAplicacoes));

        assertEquals(List.of("aplicacoes"), erro.getCamposFaltando());
        assertTrue(erro.getMessage().contains(extrato.getNomeFundo()));
    }

    /**
     * O primeiro fundo tem duas linhas de movimentacao "Aplicação"
     * (50.000,00 e 28.763,14) antes do resumo. Nenhuma delas vale 78.763,14 —
     * so a soma vale — entao o valor correto so pode ter vindo do
     * "Aplicações (+)" do resumo, e nao de uma linha avulsa.
     *
     * Tirando o rotulo do resumo, o campo tem que ficar null: cair na
     * movimentacao gravaria um numero plausivel e errado na planilha, que e
     * pior do que falhar.
     */
    @Test
    @DisplayName("movimentacao Aplicação nao substitui Aplicações (+) do resumo")
    void movimentacaoAplicacaoNaoSubstituiOResumo() {
        assertFalse(texto.contains("Aplicação 78.763,14"),
                "a fixture precisa manter o total do resumo diferente de cada"
                        + " linha de movimentacao, senao este teste nao prova nada");

        DocumentoContexto semResumo = new DocumentoContexto(
                NOME_ARQUIVO, texto.replace(LINHA_APLICACOES_RESUMO, ""));

        ExtratoInvestimento extrato = parser.processarTodos(semResumo).get(0);

        assertTrue(semResumo.getTexto().contains("Aplicação 50.000,00"));
        assertTrue(semResumo.getTexto().contains("Aplicação 28.763,14"));

        assertNull(extrato.getAplicacoes(),
                "sem o resumo, aplicacoes veio de uma linha de movimentacao:"
                        + " o valor consolidado ficou errado sem ninguem perceber");
    }
}
