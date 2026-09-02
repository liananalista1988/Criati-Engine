package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Prende a tolerancia a variacao de caixa nos rotulos acentuados do
 * CaixaParser.
 *
 * Pattern.CASE_INSENSITIVE, sozinho, so dobra caixa em US-ASCII: para
 * caracteres fora do ASCII e preciso Pattern.UNICODE_CASE. Varios rotulos
 * deste parser tem letra acentuada — "Aplicações", "Rendimento Bruto no
 * Mês", "Mês/Ano", "No Mês(%)", "Nos Últimos 12 Meses(%)" — e todos estao
 * escritos em caixa mista no padrao. Bastava a CAIXA passar a imprimir o
 * documento em caixa alta para esses campos virarem null, exatamente como
 * aconteceu com "APLICAÇÕES (+)" no extrato do Banco do Brasil.
 *
 * Metodo: a MESMA fixture real do projeto, convertida inteira para caixa
 * alta e para caixa baixa. Nao ha fixture inventada aqui — o que muda e so
 * a caixa das letras, e os valores esperados sao os mesmos da fixture
 * original.
 *
 * LACUNA CONHECIDA: os rotulos "Data Início" e "Rentabilidade Últimos 12
 * meses" pertencem ao layout "Extrato Mensal" (processarExtratoMensal), que
 * nao tem fixture nem amostra no repositorio. Eles ganham a mesma protecao,
 * porque a flag vale para o parser inteiro, mas nao ha material real para
 * prende-los em teste — inventar uma fixture desse layout seria supor a
 * estrutura de um documento que ninguem aqui viu.
 */
class CaixaParserVariacaoDeCaixaTest {

    private static final String NOME_ARQUIVO =
            "CAIXA - Extrato de Fundos IDKA IPCA 2A - JULHO.pdf";

    /** pt-BR para a conversao de caixa nao depender do locale da maquina. */
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static String texto;

    private final CaixaParser parser = new CaixaParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = CaixaParserVariacaoDeCaixaTest.class.getResourceAsStream(
                "/caixa-extrato-fundo-investimento.txt")) {

            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ExtratoInvestimento processar(String conteudo) {
        return parser.processar(new DocumentoContexto(NOME_ARQUIVO, conteudo));
    }

    /**
     * Os campos cobrados aqui sao exatamente os que a auditoria viu cair
     * quando o documento e convertido para caixa alta:
     *
     *   aplicacoes            "Aplicações"                 (Ç e Õ)
     *   rendimentos           "Rendimento Bruto no Mês"    (Ê)
     *   competencia           "Mês/Ano"                    (Ê)
     *   as 3 rentabilidades   "No Mês(%)" / "Nos Últimos 12 Meses(%)"
     *
     * saldoInicial, resgates e saldoFinal ficam de fora da lista de
     * vulneraveis porque seus rotulos sao ASCII puro — mas sao cobrados do
     * mesmo jeito, para garantir que a correcao nao os quebre.
     */
    @Test
    @DisplayName("documento inteiro em CAIXA ALTA nao perde nenhum campo")
    void caixaAltaNaoPerdeCampos() {
        exigirTodosOsCampos(processar(texto.toUpperCase(PT_BR)));
    }

    @Test
    @DisplayName("documento inteiro em caixa baixa nao perde nenhum campo")
    void caixaBaixaNaoPerdeCampos() {
        exigirTodosOsCampos(processar(texto.toLowerCase(PT_BR)));
    }

    @Test
    @DisplayName("a fixture original, em caixa mista, continua igual")
    void caixaOriginalNaoMuda() {
        exigirTodosOsCampos(processar(texto));
    }

    private void exigirTodosOsCampos(ExtratoInvestimento extrato) {
        assertAll("nenhum campo pode depender da caixa das letras do documento",
                () -> assertEquals("07/2026", extrato.getCompetencia(),
                        "competencia: rotulo \"Mês/Ano\""),
                () -> assertEquals("1.000.000,00", extrato.getSaldoInicial(),
                        "saldoInicial: rotulo \"Saldo Anterior\""),
                () -> assertEquals("0,00", extrato.getAplicacoes(),
                        "aplicacoes: rotulo \"Aplicações\""),
                () -> assertEquals("0,00", extrato.getResgates(),
                        "resgates: rotulo \"Resgates\""),
                () -> assertEquals("10.000,00", extrato.getRendimentos(),
                        "rendimentos: rotulo \"Rendimento Bruto no Mês\""),
                () -> assertEquals("1.010.000,00", extrato.getSaldoFinal(),
                        "saldoFinal: rotulo \"Saldo Bruto*\""),
                () -> assertEquals("1,3228", extrato.getRentabilidadeMes(),
                        "rentabilidadeMes: cabecalho \"No Mês(%)\""),
                () -> assertEquals("7,3742", extrato.getRentabilidadeAno(),
                        "rentabilidadeAno: cabecalho \"No Ano(%)\""),
                () -> assertEquals("12,7076", extrato.getRentabilidade12Meses(),
                        "rentabilidade12Meses: cabecalho \"Nos Últimos 12 Meses(%)\""));
    }
}
