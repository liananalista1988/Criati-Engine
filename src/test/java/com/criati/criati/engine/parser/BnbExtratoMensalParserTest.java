package com.criati.criati.engine.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

class BnbExtratoMensalParserTest {

    private static final String NOME_ARQUIVO = "bnb-extrato-mensal-sanitizado.pdf";

    private static String texto;

    private final BnbExtratoMensalParser parser = new BnbExtratoMensalParser();

    @BeforeAll
    static void carregarFixture() throws IOException {
        try (InputStream in = BnbExtratoMensalParserTest.class.getResourceAsStream(
                "/bnb-extrato-mensal-ordenado.txt")) {
            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DocumentoContexto contexto() {
        return new DocumentoContexto(NOME_ARQUIVO, texto);
    }

    @Test
    @DisplayName("extrai os campos do Extrato Mensal BNB em ordem visual")
    void extraiCamposDoExtratoMensal() {
        ExtratoInvestimento extrato = parser.processar(contexto());

        assertAll(
                () -> assertEquals("08/2026", extrato.getCompetencia()),
                () -> assertNotEquals("09/2026", extrato.getCompetencia()),
                () -> assertEquals("000123456-7", extrato.getConta()),
                () -> assertEquals("BNB", extrato.getInstituicao()),
                () -> assertEquals("BNB SOBERANO FIF", extrato.getNomeFundo()),
                () -> assertEquals("12.345.678/0001-90", extrato.getCnpjFundo()),
                () -> assertEquals("ADMINISTRADORA SANITIZADA DTVM", extrato.getAdministrador()),
                () -> assertEquals("98.765.432/0001-10", extrato.getCnpjAdministrador()),
                () -> assertNotEquals(extrato.getCnpjFundo(), extrato.getCnpjAdministrador()),
                () -> assertEquals("8.409.172,78", extrato.getSaldoInicial()),
                () -> assertEquals("0,00", extrato.getAplicacoes()),
                () -> assertEquals("0,00", extrato.getResgates()),
                () -> assertEquals("91.928,09", extrato.getRendimentos()),
                () -> assertEquals("8.501.100,87", extrato.getSaldoFinal()),
                () -> assertEquals(1.0932, numero(extrato.getRentabilidadeMes()), 0.0001));
    }

    @Test
    @DisplayName("reconhece e valida a fixture sanitizada sem campos incompletos")
    void reconheceEValida() {
        DocumentoContexto contexto = contexto();
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertTrue(parser.suporta(contexto));
        assertDoesNotThrow(() -> parser.validar(extrato, contexto));
    }

    @Test
    @DisplayName("mantem compatibilidade com texto ordenado por OCR sem acentos")
    void mantemCompatibilidadeComTextoOcr() {
        String textoOcr = Normalizer.normalize(
                        texto.toUpperCase(Locale.forLanguageTag("pt-BR")),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        DocumentoContexto contexto = new DocumentoContexto(NOME_ARQUIVO, textoOcr);
        ExtratoInvestimento extrato = parser.processar(contexto);

        assertAll(
                () -> assertTrue(parser.suporta(contexto)),
                () -> assertEquals("08/2026", extrato.getCompetencia()),
                () -> assertEquals("000123456-7", extrato.getConta()),
                () -> assertEquals("0,00", extrato.getAplicacoes()),
                () -> assertEquals("0,00", extrato.getResgates()),
                () -> assertEquals("91.928,09", extrato.getRendimentos()),
                () -> assertDoesNotThrow(() -> parser.validar(extrato, contexto)));
    }

    private double numero(String valor) {
        return Double.parseDouble(valor.replace(".", "").replace(",", "."));
    }
}
