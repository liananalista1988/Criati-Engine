package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * Reproduz o que /engine/extrato-investimento faz: processa e so entao
     * valida. A validacao saiu de dentro de processar() para que
     * /engine/processar continue devolvendo o que conseguiu ler.
     */
    private ExtratoInvestimento processarEValidar(String conteudo) {
        DocumentoContexto contexto = new DocumentoContexto(NOME_ARQUIVO, conteudo);
        ExtratoInvestimento extrato = parser.processar(contexto);

        parser.validar(extrato, contexto);

        return extrato;
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

    /**
     * Troca so o valor do "Rendimento Bruto no Mes", preservando o rotulo e o
     * espacamento original — que inclui espaco nao separavel (U+00A0), como o
     * PDFBox devolve.
     *
     * Um replace simples de "10.000,00C" nao serve: essa sequencia tambem e
     * sufixo de "1.010.000,00C", o saldo bruto da linha de baixo, e o teste
     * acabaria mexendo nos dois.
     */
    private static String trocarRendimento(String valor) {
        return texto.replaceAll(
                "(?<rotulo>Rendimento Bruto no M[eê]s[\\s ]+)"
                        + "[0-9\\.]+,[0-9]{2}C",
                "${rotulo}" + valor);
    }

    /**
     * Valores do extrato real de 06/2026 do IDKA IPCA 2A, o unico mes negativo
     * que apareceu: rendimento "5.228,39D" e saldo caindo de 4.291.710,11 para
     * 4.286.481,72. A subtracao fecha, entao o D e negativo mesmo.
     */
    @Test
    @DisplayName("le o D de debito como valor negativo")
    void leDebitoComoNegativo() {
        ExtratoInvestimento extrato = processar(trocarRendimento("5.228,39D"));

        assertEquals("-5.228,39", extrato.getRendimentos());

        // O C de credito continua positivo, e sem marcador nenhum tambem.
        assertEquals("1.000.000,00", extrato.getSaldoInicial());
        assertEquals("1.010.000,00", extrato.getSaldoFinal());
        assertEquals("0,00", extrato.getAplicacoes());
    }

    @Test
    @DisplayName("le o hifen posposto ao valor como negativo")
    void leHifenPospostoComoNegativo() {
        assertEquals("-5.228,39",
                processar(trocarRendimento("5.228,39-")).getRendimentos());
    }

    @Test
    @DisplayName("nao le o C de \"Cota em:\" da linha seguinte como sinal do valor")
    void naoConfundeLinhaSeguinteComSinal() {
        // Regressao: com \s* antes do marcador, o padrao atravessaria a quebra
        // de linha e qualquer palavra iniciada por C ou D viraria sinal.
        assertEquals("10.000,00",
                processar(trocarRendimento("10.000,00\nCota em: 30/06/2026"))
                        .getRendimentos());
    }

    @Test
    @DisplayName("le o sinal posposto da rentabilidade")
    void leRentabilidadeNegativa() {
        // "0,1218-" no extrato de 06/2026.
        assertEquals("-0,1218",
                processar(texto.replace("1,3228", "0,1218-")).getRentabilidadeMes());
    }

    @Test
    @DisplayName("falha alto quando a conta nao pode ser extraida")
    void falhaQuandoContaNaoExtraida() {
        String semConta = texto.replace("Conta Corrente", "XXXX");

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> processarEValidar(semConta));

        assertTrue(erro.getCamposFaltando().contains("conta"));
        assertTrue(erro.getMessage().contains(NOME_ARQUIVO));
    }

    @Test
    @DisplayName("falha alto quando a rentabilidade nao pode ser extraida")
    void falhaQuandoRentabilidadeNaoExtraida() {
        String semRentabilidade = texto.replace("No Mês(%)", "XXXX");

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> processarEValidar(semRentabilidade));

        assertTrue(erro.getCamposFaltando().contains("rentabilidadeMes"));
    }

    @Test
    @DisplayName("processar sozinho nao lanca: /engine/processar e diagnostico")
    void processarNaoValida() {
        // Sem esta separacao, um extrato incompleto derrubaria tambem o
        // endpoint que existe justamente para investigar por que ele esta
        // incompleto.
        String semConta = texto.replace("Conta Corrente", "XXXX");

        assertNull(processar(semConta).getConta());
    }
}
