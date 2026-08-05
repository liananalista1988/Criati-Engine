package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Fixture: texto extraido pelo PDFBox de um "Consultas - Investimentos
 * Fundos - Mensal" do Banco do Brasil (competencia 07/2026) com DOIS fundos
 * no mesmo documento, que e o caso de risco deste layout.
 *
 * Conta, nome do cotista, identificador da transacao, nome do atendente e
 * todos os valores foram trocados por dados ficticios — este repositorio e
 * publico. Nomes e CNPJs dos fundos sao dado publico e ficaram como no
 * original. As quebras de linha seguem identicas ao documento.
 */
class BancoBrasilParserTest {

    private static final String NOME_ARQUIVO = "9876-5 C.I.pdf";

    private static String texto;

    private final BancoBrasilParser parser = new BancoBrasilParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = BancoBrasilParserTest.class.getResourceAsStream(
                "/bb-investimentos-fundos-mensal.txt")) {

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
    @DisplayName("reconhece o layout Investimentos Fundos - Mensal do BB")
    void suportaLayout() {
        assertTrue(parser.suporta(contexto()));
    }

    @Test
    @DisplayName("devolve uma posicao por fundo do documento")
    void devolveUmaPosicaoPorFundo() {
        assertEquals(2, processarTodos().size());
    }

    /**
     * Prende o formato da conta, que compoe a chave da posicao no consumidor
     * (competencia + conta + CNPJ). Se o parser passar a devolver
     * "1234-5 9876-5", "09876-5" ou a agencia junto, toda linha ja gravada
     * fica inalcancavel e a reimportacao duplica em vez de atualizar.
     *
     * Note que a agencia ("Agência 1234-5") aparece na linha ANTERIOR a da
     * conta e tem o mesmo formato: e a captura errada mais provavel aqui.
     */
    @Test
    @DisplayName("le a conta sem a agencia e no formato digitos-digito")
    void extraiContaNoFormatoDoContrato() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertEquals("9876-5", extrato.getConta());
        }
    }

    /**
     * Conta e competencia aparecem uma unica vez, no cabecalho, e sao
     * copiadas para todos os fundos. E por isso que uma falha na leitura do
     * cabecalho estraga o documento inteiro, e nao um item so.
     */
    @Test
    @DisplayName("conta e competencia do cabecalho valem para todos os fundos")
    void cabecalhoEhCopiadoParaTodosOsItens() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertEquals("07/2026", extrato.getCompetencia());
            assertEquals("BB", extrato.getInstituicao());
        }
    }

    @Test
    @DisplayName("le os campos do primeiro fundo")
    void extraiPrimeiroFundo() {
        ExtratoInvestimento extrato = processarTodos().get(0);

        assertEquals("BB Previd RF Perfil", extrato.getNomeFundo());
        assertEquals("13.077.418/0001-49", extrato.getCnpjFundo());
        assertEquals("1.000.000,00", extrato.getSaldoInicial());
        assertEquals("0,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
        assertEquals("10.000,00", extrato.getRendimentos());
        assertEquals("1.010.000,00", extrato.getSaldoFinal());
        assertEquals("1,0000", extrato.getRentabilidadeMes());
        assertEquals("8,0000", extrato.getRentabilidadeAno());
        assertEquals("14,0000", extrato.getRentabilidade12Meses());
    }

    /**
     * O segundo fundo tem tabela de movimentacao, com varias linhas
     * "APLICAÇÃO" e "RESGATE" avulsas antes do "Resumo do mes". Os valores
     * cobrados aqui sao os do resumo: se alguma linha de movimentacao
     * vazasse para aplicacoes/resgates, o saldo da planilha fecharia errado
     * sem ninguem perceber.
     */
    @Test
    @DisplayName("le o resumo do segundo fundo, nao as linhas de movimentacao")
    void extraiSegundoFundoSemVazarMovimentacao() {
        ExtratoInvestimento extrato = processarTodos().get(1);

        assertEquals("BB RF Fluxo Sb", extrato.getNomeFundo());
        assertEquals("63.197.387/0001-38", extrato.getCnpjFundo());
        assertEquals("2.000.000,00", extrato.getSaldoInicial());
        assertEquals("500.000,00", extrato.getAplicacoes());
        assertEquals("300.000,00", extrato.getResgates());
        assertEquals("20.000,00", extrato.getRendimentos());
        assertEquals("2.220.000,00", extrato.getSaldoFinal());
        assertEquals("1,1000", extrato.getRentabilidadeMes());
        assertEquals("6,0000", extrato.getRentabilidadeAno());
        assertEquals("6,0000", extrato.getRentabilidade12Meses());
    }

    /**
     * Os fundos do segundo bloco nao podem herdar valores do primeiro. Como
     * os blocos sao recortados por posicao no texto, um recorte errado se
     * manifestaria como dois itens com o mesmo saldo.
     */
    @Test
    @DisplayName("os dois fundos nao compartilham valores")
    void fundosNaoSeMisturam() {
        List<ExtratoInvestimento> extratos = processarTodos();

        assertTrue(
                !extratos.get(0).getSaldoFinal().equals(extratos.get(1).getSaldoFinal()),
                "os dois fundos sairam com o mesmo saldo final: o recorte dos"
                        + " blocos por fundo provavelmente quebrou");
    }

    @Test
    @DisplayName("documento completo passa na validacao dos dois fundos")
    void validarAceitaDocumentoCompleto() {
        for (ExtratoInvestimento extrato : processarTodos()) {
            assertDoesNotThrow(() -> parser.validar(extrato, contexto()));
        }
    }

    /**
     * Falha de cabecalho reprova todos os itens, nao um so — e a mensagem
     * cita o fundo, porque com varios fundos no arquivo "faltou a conta" sem
     * dizer onde nao ajuda ninguem.
     */
    @Test
    @DisplayName("conta ilegivel reprova todos os fundos, citando cada um")
    void contaIlegivelReprovaTodosOsFundos() {
        DocumentoContexto semConta =
                new DocumentoContexto(NOME_ARQUIVO, texto.replace("Conta 9876-5", "XXXX"));

        List<ExtratoInvestimento> extratos = parser.processarTodos(semConta);

        assertEquals(2, extratos.size());

        for (ExtratoInvestimento extrato : extratos) {
            ExtratoIncompletoException erro = assertThrows(
                    ExtratoIncompletoException.class,
                    () -> parser.validar(extrato, semConta));

            assertEquals(List.of("conta"), erro.getCamposFaltando());
            assertTrue(erro.getMessage().contains(extrato.getNomeFundo()));
        }
    }
}
