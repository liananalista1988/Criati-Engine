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
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/** Nome e CNPJ do fundo sao publicos; cliente e conta da fixture sao ficticios. */
class CaixaParserNomeFundoTest {

    private static final String NOME_FUNDO = "CX FIC TOP PRIV REF DI LP";
    private static final String CNPJ_FUNDO = "19.769.018/0001-80";
    private static String texto;
    private final CaixaParser parser = new CaixaParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = CaixaParserNomeFundoTest.class.getResourceAsStream(
                "/caixa-extrato-fundo-top-di.txt")) {
            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto(String conteudo) {
        return new DocumentoContexto("caixa-top-di-sanitizado.pdf", conteudo);
    }

    private ExtratoInvestimento processar(String conteudo) {
        return parser.processar(contexto(conteudo));
    }

    @Test
    void extraiEValidaTopDiComTodosOsCamposFinanceiros() {
        DocumentoContexto contexto = contexto(texto);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertAll(
                () -> assertTrue(parser.suporta(contexto)),
                () -> assertEquals("CAIXA", extrato.getInstituicao()),
                () -> assertEquals("08/2026", extrato.getCompetencia()),
                () -> assertEquals("111222333-7", extrato.getConta()),
                () -> assertEquals(NOME_FUNDO, extrato.getNomeFundo()),
                () -> assertEquals(CNPJ_FUNDO, extrato.getCnpjFundo()),
                () -> assertEquals("0,00", extrato.getSaldoInicial()),
                () -> assertEquals("30.000.000,00", extrato.getAplicacoes()),
                () -> assertEquals("0,00", extrato.getResgates()),
                () -> assertEquals("96.281,88", extrato.getRendimentos()),
                () -> assertEquals("30.096.281,88", extrato.getSaldoFinal()),
                () -> assertEquals("1,1333", extrato.getRentabilidadeMes()),
                () -> assertEquals("9,4562", extrato.getRentabilidadeAno()),
                () -> assertEquals("14,8055", extrato.getRentabilidade12Meses()),
                () -> assertDoesNotThrow(() -> parser.validar(extrato, contexto)));

        BigDecimal fechamento = numero(extrato.getSaldoInicial())
                .add(numero(extrato.getAplicacoes()))
                .subtract(numero(extrato.getResgates()))
                .add(numero(extrato.getRendimentos()));
        assertEquals(numero(extrato.getSaldoFinal()), fechamento);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CAIXA FIC BRASIL DISPONIBILIDADES R | 14.508.643/0001-55",
            "CAIXA FI BRASIL IDKA IPCA 2A RF LP | 14.386.926/0001-71",
            "CAIXA FI BRASIL IRF-M1 TP RF | 10.740.670/0001-06",
            "FUNDO SANITIZADO REFERENCIADO DI | 12.345.678/0001-90"
    })
    void campoEstruturalNaoDependeDePrefixoOuCnpj(String nome, String cnpj) {
        String conteudo = texto.replace(NOME_FUNDO, nome).replace(CNPJ_FUNDO, cnpj);
        DocumentoContexto contexto = contexto(conteudo);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertEquals(nome, extrato.getNomeFundo());
        assertEquals(cnpj, extrato.getCnpjFundo());
        assertDoesNotThrow(() -> parser.validar(extrato, contexto));
    }

    @Test
    void palavraFundoNoTituloNaoSubstituiRotuloDoCampo() {
        String semRotulo = texto.replace("\nFundo\n", "\nProduto\n");
        DocumentoContexto contexto = contexto(semRotulo);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertTrue(semRotulo.startsWith("Extrato Fundo de Investimento"));
        assertNull(extrato.getNomeFundo());
        ExtratoIncompletoException erro = assertThrows(ExtratoIncompletoException.class,
                () -> parser.validar(extrato, contexto));
        assertEquals(java.util.List.of("nomeFundo"), erro.getCamposFaltando());
    }

    @Test
    void aceitaNomeEmDuasLinhasERotulosComEspacosNaoSeparaveis() {
        String conteudo = texto
                .replace("\nFundo\n", "\n\u00a0Fundo\u00a0\n")
                .replace(NOME_FUNDO, "CX FIC TOP PRIV\nREF DI LP")
                .replace("CNPJ do Fundo", "\u00a0CNPJ do Fundo");

        assertEquals(NOME_FUNDO, processar(conteudo).getNomeFundo());
    }

    @Test
    void rotuloEFronteiraFuncionamEmCaixaAltaEBaixa() {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        assertEquals(NOME_FUNDO, processar(texto.toUpperCase(ptBr)).getNomeFundo());
        assertEquals(NOME_FUNDO.toLowerCase(ptBr),
                processar(texto.toLowerCase(ptBr)).getNomeFundo());
    }

    @Test
    void campoEstruturalTemPrecedenciaSobreNomeSoltoDeFallback() {
        String sentinela = "CAIXA FI BRASIL SENTINELA 12.345.678/0001-90\n";
        assertEquals(NOME_FUNDO, processar(sentinela + texto).getNomeFundo());
    }

    @Test
    void preservaFallbackLegadoDeCnpjQuandoNaoHaNomeEstruturado() {
        String legado = texto.replace("\nFundo\n", "\nProduto\n")
                .replace(NOME_FUNDO, "DENOMINACAO NAO DISPONIVEL")
                .replace(CNPJ_FUNDO, "10.740.670/0001-06");

        assertEquals("CAIXA FI BRASIL IRF-M1 TP RF", processar(legado).getNomeFundo());
    }

    private BigDecimal numero(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }
}
