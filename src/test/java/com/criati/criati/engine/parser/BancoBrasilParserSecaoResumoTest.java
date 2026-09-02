package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Prende a fonte canonica dos cinco campos consolidados do layout mensal do
 * BB: eles tem que sair da secao "Resumo do mes" do fundo, e de lugar nenhum
 * mais.
 *
 * O bloco de um fundo carrega duas naturezas de dado que usam o mesmo
 * vocabulario: a tabela de movimentacoes, lancamento a lancamento, e o
 * "Resumo do mes", consolidado. Enquanto os campos eram extraidos do bloco
 * inteiro, cada um se defendia sozinho com algum detalhe do proprio rotulo —
 * "(+)", "(-)", "=", "BRUTO" — que por acaso a tabela nao repetia. Saldo
 * anterior nao tinha detalhe nenhum: e escrito igual nos dois lugares, entao
 * o parser lia o da movimentacao e, pior, a validacao aprovava.
 *
 * As fixtures desta classe sao SINTETICAS de proposito. Elas repetem, antes
 * do resumo, os cinco rotulos com valores-sentinela diferentes. Nenhum
 * extrato do BB e assim hoje — e essa e justamente a questao: testar so com
 * "Aplicação" e "Resgate", como o documento real traz, nao prova nada,
 * porque esses ja nao colidiam com os rotulos do resumo. O que precisa ficar
 * preso e que a separacao vale por SECAO, e nao pela sorte de o rotulo da
 * tabela ser diferente.
 *
 * Duas fixtures, uma por layout, porque o recorte tem que servir aos dois:
 * no layout anterior os itens vem em caixa alta e no atual em caixa mista,
 * mas o rotulo da secao e "Resumo do mês" nos dois.
 */
class BancoBrasilParserSecaoResumoTest {

    private static final String NOME_ARQUIVO = "9876-5 C.I.pdf";

    private static String sentinelasAtual;
    private static String sentinelasAnterior;
    private static String layoutAtual;

    private final BancoBrasilParser parser = new BancoBrasilParser();

    @BeforeAll
    static void carregarFixtures() throws IOException {
        sentinelasAtual = ler("/bb-sentinelas-resumo-layout-atual.txt");
        sentinelasAnterior = ler("/bb-sentinelas-resumo-layout-anterior.txt");
        layoutAtual = ler("/bb-investimentos-fundos-mensal-layout-atual.txt");
    }

    private static String ler(String recurso) throws IOException {
        try (InputStream in = BancoBrasilParserSecaoResumoTest.class.getResourceAsStream(recurso)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto(String texto) {
        return new DocumentoContexto(NOME_ARQUIVO, texto);
    }

    private List<ExtratoInvestimento> processar(String texto) {
        return parser.processarTodos(contexto(texto));
    }

    /**
     * A prova estrutural. Os mesmos cinco rotulos aparecem duas vezes no
     * bloco do fundo, com valores diferentes: 11x.xxx,xx antes do resumo e
     * 22x.xxx,xx dentro dele. So os 22x sao validos.
     *
     * Contra o parser que extraia do bloco inteiro, os cinco campos saem
     * 11x.xxx,xx — os cinco falham juntos.
     */
    @Test
    @DisplayName("rotulos iguais antes do resumo nao vencem o Resumo do mes (layout atual)")
    void sentinelasAntesDoResumoNaoVencemOResumoNoLayoutAtual() {
        exigirValoresDoResumo(processar(sentinelasAtual).get(0));
    }

    @Test
    @DisplayName("rotulos iguais antes do resumo nao vencem o Resumo do mes (layout anterior)")
    void sentinelasAntesDoResumoNaoVencemOResumoNoLayoutAnterior() {
        exigirValoresDoResumo(processar(sentinelasAnterior).get(0));
    }

    /**
     * assertAll de proposito: os cinco campos tem a mesma causa, entao parar
     * no primeiro esconderia quais dos outros tambem estao lendo fora da
     * secao. Com assertAll o relatorio mostra os cinco de uma vez.
     */
    private void exigirValoresDoResumo(ExtratoInvestimento extrato) {
        assertAll("consolidados devem vir da secao Resumo do mes (22x), nao de antes dela (11x)",
                () -> assertEquals("221.111,11", extrato.getSaldoInicial(),
                        "saldoInicial veio de fora do resumo"),
                () -> assertEquals("222.222,22", extrato.getAplicacoes(),
                        "aplicacoes veio de fora do resumo"),
                () -> assertEquals("223.333,33", extrato.getResgates(),
                        "resgates veio de fora do resumo"),
                () -> assertEquals("224.444,44", extrato.getRendimentos(),
                        "rendimentos veio de fora do resumo"),
                () -> assertEquals("225.555,55", extrato.getSaldoFinal(),
                        "saldoFinal veio de fora do resumo"));
    }

    /**
     * O recorte da secao nao pode atravessar a fronteira do fundo: o segundo
     * fundo tem os seus proprios sentinelas (33x fora do resumo, 44x dentro)
     * e nao pode sair com nada do primeiro.
     */
    @Test
    @DisplayName("o recorte do resumo nao ultrapassa o bloco do fundo")
    void segundoFundoNaoHerdaNadaDoPrimeiro() {
        List<ExtratoInvestimento> extratos = processar(sentinelasAtual);

        assertEquals(2, extratos.size());

        ExtratoInvestimento segundo = extratos.get(1);

        assertEquals("441.111,11", segundo.getSaldoInicial());
        assertEquals("442.222,22", segundo.getAplicacoes());
        assertEquals("443.333,33", segundo.getResgates());
        assertEquals("444.444,44", segundo.getRendimentos());
        assertEquals("445.555,55", segundo.getSaldoFinal());

        assertNotEquals(extratos.get(0).getSaldoFinal(), segundo.getSaldoFinal());
    }

    /**
     * O recorte mexe so nos consolidados. "No mes", "No ano" e "Últimos 12
     * meses" continuam vindo da secao Rentabilidade, que fica DEPOIS do fim
     * do resumo — se passassem a ser lidos de dentro do recorte, voltariam
     * null.
     */
    @Test
    @DisplayName("rentabilidades continuam vindo da secao Rentabilidade")
    void rentabilidadesNaoMudamDeFonte() {
        List<ExtratoInvestimento> extratos = processar(sentinelasAtual);

        assertEquals("1,2500", extratos.get(0).getRentabilidadeMes());
        assertEquals("7,5000", extratos.get(0).getRentabilidadeAno());
        assertEquals("12,5000", extratos.get(0).getRentabilidade12Meses());

        assertEquals("1,0000", extratos.get(1).getRentabilidadeMes());
        assertEquals("8,0000", extratos.get(1).getRentabilidadeAno());
        assertEquals("13,0000", extratos.get(1).getRentabilidade12Meses());
    }

    /**
     * O caso realista do documento do BB: "Saldo anterior" abre a tabela de
     * movimentacoes e se repete no resumo. Hoje os dois valores coincidem, o
     * que mascara o problema; aqui eles sao separados de proposito.
     */
    @Test
    @DisplayName("saldo anterior divergente entre movimentacao e resumo usa o do resumo")
    void saldoAnteriorDivergenteUsaOResumo() {
        String texto = layoutAtual
                .replace("31/07/2026 Saldo anterior 100.000,00",
                         "31/07/2026 Saldo anterior 111.111,11")
                .replace("Saldo anterior 100.000,00", "Saldo anterior 222.222,22");

        ExtratoInvestimento extrato = processar(texto).get(0);

        assertEquals("222.222,22", extrato.getSaldoInicial());
    }

    /**
     * Pior caso da Etapa 2: sem "Saldo anterior" no resumo, o parser antigo
     * pegava o da movimentacao e a validacao APROVAVA — gravava um numero de
     * fonte errada sem null, sem excecao e sem sinal nenhum. Tem que virar
     * null e reprovar.
     */
    @Test
    @DisplayName("saldo anterior so fora do resumo fica null e reprova o fundo")
    void saldoAnteriorForaDoResumoNaoPreencheOCampo() {
        String texto = layoutAtual
                .replace("31/07/2026 Saldo anterior 100.000,00",
                         "31/07/2026 Saldo anterior 111.111,11")
                .replace("Saldo anterior 100.000,00", "");

        DocumentoContexto contexto = contexto(texto);
        ExtratoInvestimento extrato = parser.processarTodos(contexto).get(0);

        assertNull(extrato.getSaldoInicial(),
                "saldoInicial foi preenchido com a linha da movimentacao");

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));

        assertEquals(List.of("saldoInicial"), erro.getCamposFaltando());
    }

    /**
     * Sem a secao inteira, os cinco consolidados somem juntos — e nenhum
     * deles pode ser preenchido com numero do fundo seguinte, que vem logo
     * abaixo no texto.
     */
    @Test
    @DisplayName("fundo sem a secao Resumo do mes perde os cinco consolidados")
    void semSecaoResumoOsCincoConsolidadosFicamNulos() {
        String texto = layoutAtual
                .replace("31/07/2026 Saldo anterior 100.000,00",
                         "31/07/2026 Saldo anterior 111.111,11")
                .replace("Saldo anterior 100.000,00", "")
                .replace("Aplicações (+) 78.763,14", "")
                .replace("Resgates (-) 20.000,00", "")
                .replace("Rendimento Bruto (+) 1.236,86", "")
                .replace("Saldo Atual = 160.000,00", "")
                .replaceFirst("Resumo do mês", "");

        DocumentoContexto contexto = contexto(texto);
        List<ExtratoInvestimento> extratos = parser.processarTodos(contexto);

        ExtratoInvestimento primeiro = extratos.get(0);

        assertNull(primeiro.getSaldoInicial());
        assertNull(primeiro.getAplicacoes());
        assertNull(primeiro.getResgates());
        assertNull(primeiro.getRendimentos());
        assertNull(primeiro.getSaldoFinal());

        // o fundo seguinte esta intacto: se algum campo acima tivesse
        // vazado, sairia com estes numeros
        ExtratoInvestimento segundo = extratos.get(1);

        assertEquals("200.000,00", segundo.getSaldoInicial());
        assertEquals("0,00", segundo.getAplicacoes());
        assertEquals("2.000,00", segundo.getRendimentos());
        assertEquals("202.000,00", segundo.getSaldoFinal());

        ExtratoIncompletoException erro = assertThrows(
                ExtratoIncompletoException.class,
                () -> parser.validar(primeiro, contexto));

        assertEquals(
                List.of("saldoInicial", "aplicacoes", "resgates", "rendimentos", "saldoFinal"),
                erro.getCamposFaltando());

        assertTrue(erro.getMessage().contains(primeiro.getNomeFundo()));
    }
}
