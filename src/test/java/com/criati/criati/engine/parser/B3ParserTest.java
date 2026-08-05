package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Fixture: texto extraido pelo PDFBox de um "Relatorio mensal consolidado"
 * da B3 (competencia 06/2026), com as quebras de linha exatamente como o
 * PDFBox devolve.
 *
 * Nome e CNPJ do cotista, quantidades e valores foram trocados por dados
 * ficticios — este repositorio e publico. O ticker e o nome do fundo sao
 * dado publico de mercado e ficaram como no original, porque sao
 * justamente o que os testes prendem.
 */
class B3ParserTest {

    private static final String NOME_ARQUIVO =
            "relatorio-consolidado-mensal-2026-junho.pdf";

    private static String texto;

    private final B3Parser parser = new B3Parser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = B3ParserTest.class.getResourceAsStream(
                "/b3-relatorio-mensal-consolidado.txt")) {

            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto() {
        return new DocumentoContexto(NOME_ARQUIVO, texto);
    }

    private ExtratoInvestimento processar() {
        return parser.processar(contexto());
    }

    @Test
    @DisplayName("reconhece o Relatorio Mensal Consolidado da B3")
    void suportaLayout() {
        assertTrue(parser.suporta(contexto()));
    }

    @Test
    @DisplayName("le competencia, fundo e saldo com o nome do fundo quebrado em duas linhas")
    void extraiCamposDisponiveis() {
        ExtratoInvestimento extrato = processar();

        assertEquals("06/2026", extrato.getCompetencia());
        assertEquals("B3", extrato.getInstituicao());
        assertEquals(
                "RBRD11 - RB CAPITAL RENDA II FDO INV IMOB - FII RESP LTDA.",
                extrato.getNomeFundo());
        assertEquals("250.000,00", extrato.getSaldoFinal());
    }

    /**
     * A conta e literal porque o relatorio da B3 nao tem numero de conta: a
     * posicao e da custodia, nao de uma conta corrente. Prender o valor aqui
     * porque ele compoe a chave da posicao no consumidor (competencia +
     * conta + CNPJ) — mudar de "B3" para qualquer outra coisa tornaria
     * inalcancavel toda linha ja gravada.
     */
    @Test
    @DisplayName("conta e sempre o literal \"B3\"")
    void contaEhLiteral() {
        assertEquals("B3", processar().getConta());
    }

    /**
     * Os proventos entram como movimentacao (aplicacoes) por decisao do
     * layout: o rendimento real e calculado depois, pelo consumidor, como
     * (saldoAtual - saldoAnterior) + movimentacao.
     */
    @Test
    @DisplayName("proventos recebidos entram como movimentacao, nao como rendimento")
    void proventosViramMovimentacao() {
        ExtratoInvestimento extrato = processar();

        assertEquals("5.000,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
        assertNull(extrato.getRendimentos());
    }

    /**
     * Documenta a limitacao que obriga a tabela ticker -> CNPJ a existir: o
     * relatorio da B3 nao imprime o CNPJ do fundo em lugar nenhum. O unico
     * CNPJ do documento e o do cotista, no cabecalho — usa-lo como cnpjFundo
     * colocaria todos os FIIs sob a mesma chave, ja que a conta da B3 e o
     * literal "B3".
     *
     * Se algum dia o layout passar a trazer o CNPJ do fundo, este teste
     * falha e avisa que da para largar a tabela.
     */
    @Test
    @DisplayName("o documento so tem o CNPJ do cotista, nunca o do fundo")
    void documentoNaoTrazCnpjDoFundo() {
        String depoisDoCabecalho = texto.substring(texto.indexOf('\n'));

        assertFalse(
                depoisDoCabecalho.matches("(?s).*\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}.*"),
                "apareceu um CNPJ formatado fora do cabecalho: reveja se agora da"
                        + " para extrair o CNPJ do fundo do proprio documento");
    }

    @Test
    @DisplayName("o CNPJ do fundo vem da tabela por ticker")
    void cnpjVemDaTabelaPorTicker() {
        assertEquals("09.006.914/0001-34", processar().getCnpjFundo());
    }

    @Test
    @DisplayName("relatorio valido passa na validacao")
    void validarAceitaRelatorioCompleto() {
        ExtratoInvestimento extrato = processar();

        assertDoesNotThrow(() -> parser.validar(extrato, contexto()));
    }

    /**
     * Fundo novo tem que aparecer como erro visivel, nao como linha sem
     * identidade na planilha. A mensagem cita o ticker justamente para dizer
     * o que cadastrar.
     */
    @Test
    @DisplayName("ticker fora da tabela reprova o arquivo apontando cnpjFundo")
    void tickerDesconhecidoReprova() {
        String outroFundo = texto.replace("RBRD11", "XPML11");

        DocumentoContexto contexto = new DocumentoContexto(NOME_ARQUIVO, outroFundo);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertNull(extrato.getCnpjFundo());

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));

        assertEquals(List.of("cnpjFundo"), erro.getCamposFaltando());
        assertTrue(erro.getMessage().contains("XPML11"));
    }

    /**
     * Regressao do pior caso: processar() devolve UMA posicao e pega o
     * primeiro fundo que encontra. Antes desta trava, o segundo FII de um
     * relatorio era descartado em silencio.
     */
    @Test
    @DisplayName("relatorio com dois FIIs reprova em vez de perder um deles")
    void doisFundosNoMesmoRelatorioReprovam() {
        String doisFundos = texto.replace(
                "Reembolsos de empréstimos de ativos",
                "XPML11 - XP MALLS FDO INV IMOB - FII RESP LTDA.\n"
                        + "Reembolsos de empréstimos de ativos");

        DocumentoContexto contexto = new DocumentoContexto(NOME_ARQUIVO, doisFundos);
        ExtratoInvestimento extrato = parser.processar(contexto);

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));

        assertTrue(erro.getMessage().contains("RBRD11"));
        assertTrue(erro.getMessage().contains("XPML11"));
    }
}
