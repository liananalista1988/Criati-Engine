package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Layout "Extrato Mensal" da CAIXA. Fixture extraida com o PDFTextStripper
 * padrao, como no PdfService. Nome, CNPJ e rentabilidades do fundo sao
 * publicos; investidor, CNPJ do cotista, conta, saldos e cotas sao ficticios.
 */
class CaixaParserExtratoMensalTest {

    private static final String LINHA_INVESTIDOR =
            "Investidor: INSTITUTO FICTICIO - SÃO PAULO SP 111222333-7";

    /** Nome real de download: nao traz o numero da conta. */
    private static final String ARQUIVO_SEM_CONTA =
            "Extrato Mensal_I CAIXA BRASIL ESPECIAL 2028.pdf";

    private static String texto;
    private final CaixaParser parser = new CaixaParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = CaixaParserExtratoMensalTest.class.getResourceAsStream(
                "/caixa-extrato-mensal.txt")) {
            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto(String nomeArquivo, String conteudo) {
        return new DocumentoContexto(nomeArquivo, conteudo);
    }

    @Test
    void extraiEValidaOLayoutInteiroSemContaNoNomeDoArquivo() {
        DocumentoContexto contexto = contexto(ARQUIVO_SEM_CONTA, texto);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertAll(
                () -> assertTrue(parser.suporta(contexto)),
                () -> assertEquals("CAIXA", extrato.getInstituicao()),
                () -> assertEquals("CAIXA ECONOMICA FEDERAL", extrato.getAdministrador()),
                () -> assertEquals("111222333-7", extrato.getConta()),
                () -> assertEquals("10/2026", extrato.getCompetencia()),
                () -> assertEquals("CI CAIXA BRASIL ESPECIAL 2028 TP RF RL", extrato.getNomeFundo()),
                () -> assertEquals("56.209.124/0001-36", extrato.getCnpjFundo()),
                () -> assertEquals("00.360.305/0001-04", extrato.getCnpjAdministrador()),
                () -> assertEquals("30/09/2026", extrato.getDataInicio()),
                () -> assertEquals("30/10/2026", extrato.getDataFim()),
                () -> assertEquals("1.000.000,00", extrato.getSaldoInicial()),
                () -> assertEquals("0,00", extrato.getAplicacoes()),
                () -> assertEquals("0,00", extrato.getResgates()),
                () -> assertEquals("5.800,00", extrato.getRendimentos()),
                () -> assertEquals("1.005.800,00", extrato.getSaldoFinal()),
                () -> assertEquals("0,58", extrato.getRentabilidadeMes()),
                () -> assertEquals("4,98", extrato.getRentabilidadeAno()),
                () -> assertEquals("7,13", extrato.getRentabilidade12Meses()),
                () -> assertDoesNotThrow(() -> parser.validar(extrato, contexto)));

        BigDecimal fechamento = numero(extrato.getSaldoInicial())
                .add(numero(extrato.getAplicacoes()))
                .subtract(numero(extrato.getResgates()))
                .add(numero(extrato.getRendimentos()));
        assertEquals(numero(extrato.getSaldoFinal()), fechamento);
    }

    @Test
    void contaDoTextoTemPrecedenciaSobreONomeDoArquivo() {
        DocumentoContexto contexto = contexto("999888777-1 extrato.pdf", texto);

        assertEquals("111222333-7", parser.processar(contexto).getConta());
    }

    @Test
    void mantemFallbackDoNomeDoArquivoQuandoALinhaNaoTemConta() {
        String semConta = texto.replace(
                LINHA_INVESTIDOR, "Investidor: INSTITUTO FICTICIO - SÃO PAULO SP");

        DocumentoContexto contexto = contexto(
                "111222333-7 CI CAIXA BRASIL ESPECIAL 2028.pdf", semConta);

        assertEquals("111222333-7", parser.processar(contexto).getConta());
    }

    @Test
    void naoBuscaContaNaLinhaSeguinteNemRotuloSemDoisPontos() {
        String semConta = texto.replace(
                LINHA_INVESTIDOR,
                "Análise do Perfil do Investidor Protocolo 123456789-0\n"
                        + "Investidor: INSTITUTO FICTICIO - SÃO PAULO SP");
        DocumentoContexto contexto = contexto(ARQUIVO_SEM_CONTA, semConta);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertNull(extrato.getConta());
        ExtratoIncompletoException erro = assertThrows(ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));
        assertEquals(List.of("conta"), erro.getCamposFaltando());
    }

    @Test
    void contaComZerosAEsquerdaTemAMesmaChaveDoLayoutFundoDeInvestimento() {
        String comZeros = texto.replace("111222333-7", "000111222333-7");

        assertEquals("111222333-7",
                parser.processar(contexto(ARQUIVO_SEM_CONTA, comZeros)).getConta());
    }

    private BigDecimal numero(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }
}
