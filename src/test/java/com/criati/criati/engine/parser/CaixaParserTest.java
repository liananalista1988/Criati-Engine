package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Fixture: texto extraido pelo PDFBox de um "Extrato Fundo de Investimento"
 * da CAIXA (competencia 07/2026). O arquivo guarda as quebras de linha
 * exatamente como o PDFBox devolve, porque sao elas que quebravam o parser.
 *
 * Nome do cotista, CNPJ do cotista, numero da conta e saldos foram trocados
 * por valores ficticios — este repositorio e publico. As quebras de linha,
 * que sao o objeto do teste, seguem identicas ao documento original.
 */
class CaixaParserTest {

    private static final String NOME_ARQUIVO =
            "CAIXA - Extrato de Fundos IDKA IPCA 2A - JULHO.pdf";

    private static String texto;

    private final CaixaParser parser = new CaixaParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = CaixaParserTest.class.getResourceAsStream(
                "/caixa-extrato-fundo-investimento.txt")) {

            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ExtratoInvestimento processar(String conteudo) {
        return parser.processar(new DocumentoContexto(NOME_ARQUIVO, conteudo));
    }

    @Test
    @DisplayName("reconhece o layout Extrato Fundo de Investimento da CAIXA")
    void suportaLayout() {
        assertTrue(parser.suporta(new DocumentoContexto(NOME_ARQUIVO, texto)));
    }

    @Test
    @DisplayName("extrai a conta mesmo com o digito verificador em outra linha")
    void extraiContaComDigitoVerificadorQuebrado() {
        // No fixture: "Conta Corrente\n1234.000111222333-\n7"
        // O prefixo de 4 digitos e os zeros a esquerda ficam de fora.
        assertEquals("111222333-7", processar(texto).getConta());
    }

    @Test
    @DisplayName("extrai as tres rentabilidades com o cabecalho quebrado em duas linhas")
    void extraiRentabilidades() {
        ExtratoInvestimento extrato = processar(texto);

        assertEquals("1,3228", extrato.getRentabilidadeMes());
        assertEquals("7,3742", extrato.getRentabilidadeAno());
        assertEquals("12,7076", extrato.getRentabilidade12Meses());
    }

    @Test
    @DisplayName("nao confunde o ano de \"Cota em: 31/07/2026\" com a rentabilidade do mes")
    void naoCapturaAnoDaCotaComoRentabilidade() {
        // Regressao: aceitar percentual sem casa decimal fazia o ano da data
        // casar antes dos numeros reais e rentabilidadeMes virava "2026".
        assertNotEquals("2026", processar(texto).getRentabilidadeMes());
    }

    @Test
    @DisplayName("extrai os demais campos lidos pelo consumidor")
    void extraiDemaisCampos() {
        ExtratoInvestimento extrato = processar(texto);

        assertEquals("07/2026", extrato.getCompetencia());
        assertEquals("CAIXA", extrato.getInstituicao());
        assertEquals("CAIXA FI BRASIL IDKA IPCA 2A RF LP", extrato.getNomeFundo());
        assertEquals("14.386.926/0001-71", extrato.getCnpjFundo());
        assertEquals("1.000.000,00", extrato.getSaldoInicial());
        assertEquals("0,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
        assertEquals("10.000,00", extrato.getRendimentos());
        assertEquals("1.010.000,00", extrato.getSaldoFinal());
    }

    @Test
    @DisplayName("falha alto quando a conta nao pode ser extraida")
    void falhaQuandoContaNaoExtraida() {
        String semConta = texto.replace("Conta Corrente", "XXXX");

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> processar(semConta));

        assertTrue(erro.getCamposFaltando().contains("conta"));
        assertTrue(erro.getMessage().contains(NOME_ARQUIVO));
    }

    @Test
    @DisplayName("falha alto quando a rentabilidade nao pode ser extraida")
    void falhaQuandoRentabilidadeNaoExtraida() {
        String semRentabilidade = texto.replace("No Mês(%)", "XXXX");

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> processar(semRentabilidade));

        assertTrue(erro.getCamposFaltando().contains("rentabilidadeMes"));
    }
}
