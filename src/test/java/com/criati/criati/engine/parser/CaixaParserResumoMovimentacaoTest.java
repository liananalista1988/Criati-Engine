package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * Prende a fonte canonica dos cinco campos consolidados do layout "Extrato
 * Fundo de Investimento" da CAIXA: eles tem que sair da secao "Resumo da
 * Movimentacao", e de lugar nenhum mais.
 *
 * O documento repete os mesmos rotulos ("Saldo Anterior", "Aplicações",
 * "Resgates", "Rendimento Bruto no Mês", "Saldo Bruto*") em mais de um
 * lugar. Enquanto os campos eram extraidos do texto inteiro, o parser
 * acertava apenas porque o resumo aparece ANTES da "Movimentacao
 * Detalhada" — sorte de ordenacao, nao regra. Bastava um bloco com os
 * mesmos rotulos vir antes para os cinco campos irem junto, e a validacao
 * aprovava porque todos vinham preenchidos.
 *
 * Fixture SINTETICA de proposito: alem do resumo real, ela traz os mesmos
 * cinco rotulos ANTES dele (sentinelas 11x.xxx,xx) e DEPOIS dele, dentro da
 * "Movimentacao Detalhada" (sentinelas 22x.xxx,xx). Nenhum extrato da CAIXA
 * e assim — e e essa artificialidade que da valor a prova, porque testa as
 * DUAS fronteiras do recorte de uma vez. Os valores canonicos, no meio, sao
 * os mesmos da fixture real do projeto.
 *
 * Este teste cobre so o layout "Extrato Fundo de Investimento". O layout
 * "Extrato Mensal" da CAIXA nao tem fixture nem amostra no repositorio, e
 * nem sequer esta comprovado que ele possui uma secao "Resumo da
 * Movimentacao" — suporta() exige esse rotulo apenas para este layout aqui.
 */
class CaixaParserResumoMovimentacaoTest {

    private static final String NOME_ARQUIVO =
            "CAIXA - Extrato de Fundos IDKA IPCA 2A - JULHO.pdf";

    private static String sentinelas;
    private static String fixtureReal;

    private final CaixaParser parser = new CaixaParser();

    @BeforeAll
    static void carregarFixtures() throws IOException {
        sentinelas = ler("/caixa-sentinelas-resumo-movimentacao.txt");
        fixtureReal = ler("/caixa-extrato-fundo-investimento.txt");
    }

    private static String ler(String recurso) throws IOException {
        try (InputStream in =
                CaixaParserResumoMovimentacaoTest.class.getResourceAsStream(recurso)) {

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ExtratoInvestimento processar(String conteudo) {
        return parser.processar(new DocumentoContexto(NOME_ARQUIVO, conteudo));
    }

    /**
     * A prova estrutural. Os cinco rotulos aparecem tres vezes no documento,
     * com valores diferentes; so os do "Resumo da Movimentacao" valem.
     *
     * assertAll de proposito: os cinco tem a mesma causa, e parar no
     * primeiro esconderia quais dos outros tambem estao lendo fora da secao.
     */
    @Test
    @DisplayName("os cinco consolidados saem do Resumo da Movimentacao, nao do que vem antes")
    void consolidadosSaemDoResumo() {
        ExtratoInvestimento extrato = processar(sentinelas);

        assertAll("consolidados devem vir do Resumo da Movimentacao",
                () -> assertEquals("1.000.000,00", extrato.getSaldoInicial(),
                        "saldoInicial veio de fora do resumo"),
                () -> assertEquals("0,00", extrato.getAplicacoes(),
                        "aplicacoes veio de fora do resumo"),
                () -> assertEquals("0,00", extrato.getResgates(),
                        "resgates veio de fora do resumo"),
                () -> assertEquals("10.000,00", extrato.getRendimentos(),
                        "rendimentos veio de fora do resumo"),
                () -> assertEquals("1.010.000,00", extrato.getSaldoFinal(),
                        "saldoFinal veio de fora do resumo"));
    }

    /**
     * Zero do resumo tem que vencer um numero grande vindo de fora. Se o
     * recorte falhasse justamente nos campos zerados, o erro passaria
     * despercebido — 0,00 e o valor mais comum num mes sem movimento.
     */
    @Test
    @DisplayName("0,00 do resumo vence o valor de fora da secao")
    void zeroDoResumoVenceValorDeFora() {
        ExtratoInvestimento extrato = processar(sentinelas);

        assertEquals("0,00", extrato.getAplicacoes());
        assertEquals("0,00", extrato.getResgates());
    }

    /**
     * A fronteira de baixo: a "Movimentacao Detalhada" vem depois do resumo
     * e repete os mesmos rotulos. O recorte tem que parar nela.
     */
    @Test
    @DisplayName("o recorte termina na Movimentacao Detalhada")
    void recorteNaoAlcancaAMovimentacaoDetalhada() {
        ExtratoInvestimento extrato = processar(sentinelas);

        assertAll(
                () -> assertEquals("1.000.000,00", extrato.getSaldoInicial()),
                () -> assertEquals("10.000,00", extrato.getRendimentos()),
                () -> assertEquals("1.010.000,00", extrato.getSaldoFinal()));
    }

    /**
     * O contrario do bug: sem a secao, os cinco campos somem juntos em vez
     * de serem preenchidos com numeros de outra procedencia. Reprovar e
     * melhor do que gravar um valor plausivel e errado.
     */
    @Test
    @DisplayName("sem a secao Resumo da Movimentacao os cinco consolidados ficam nulos")
    void semSecaoResumoOsConsolidadosFicamNulos() {
        String texto = sentinelas.replace("Resumo da Movimentação", "");

        DocumentoContexto contexto = new DocumentoContexto(NOME_ARQUIVO, texto);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertAll(
                () -> assertNull(extrato.getSaldoInicial()),
                () -> assertNull(extrato.getAplicacoes()),
                () -> assertNull(extrato.getResgates()),
                () -> assertNull(extrato.getRendimentos()),
                () -> assertNull(extrato.getSaldoFinal()));

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));

        assertEquals(
                java.util.List.of("saldoInicial", "aplicacoes", "resgates",
                        "rendimentos", "saldoFinal"),
                erro.getCamposFaltando());
    }

    /**
     * Trava de nao-regressao: na fixture REAL do projeto, que nao tem
     * rotulo repetido nenhum, os cinco valores nao podem mudar.
     */
    @Test
    @DisplayName("a fixture real continua dando os mesmos cinco valores")
    void fixtureRealNaoMuda() {
        ExtratoInvestimento extrato = processar(fixtureReal);

        assertAll(
                () -> assertEquals("1.000.000,00", extrato.getSaldoInicial()),
                () -> assertEquals("0,00", extrato.getAplicacoes()),
                () -> assertEquals("0,00", extrato.getResgates()),
                () -> assertEquals("10.000,00", extrato.getRendimentos()),
                () -> assertEquals("1.010.000,00", extrato.getSaldoFinal()));
    }
}
