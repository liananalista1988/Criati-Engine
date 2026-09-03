package com.criati.criati.engine.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PdfServiceTest {

    private final PdfService service = new PdfService();

    @Test
    @DisplayName("ordena por posicao apenas o layout Extrato Mensal do BNB")
    void ordenaPorPosicaoSomenteParaBnbExtratoMensal() throws IOException {
        String textoBnb = extrair(pdfComColunas(true));
        String textoGenerico = extrair(pdfComColunas(false));

        assertTrue(textoBnb.indexOf("ESQUERDA") < textoBnb.indexOf("DIREITA"),
                "BNB deve seguir a ordem visual");
        assertTrue(textoGenerico.indexOf("DIREITA") < textoGenerico.indexOf("ESQUERDA"),
                "outros layouts devem preservar a ordem nativa atual");
    }

    private String extrair(byte[] pdf) throws IOException {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "fixture-sanitizada.pdf", "application/pdf", pdf);
        return service.extrairTexto(arquivo).getTexto();
    }

    private byte[] pdfComColunas(boolean bnb) throws IOException {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                if (bnb) {
                    escrever(conteudo, 50, 750, "Banco do Nordeste");
                    escrever(conteudo, 50, 730, "Sistema Fundos de Investimento");
                    escrever(conteudo, 50, 710, "Extrato Mensal");
                } else {
                    escrever(conteudo, 50, 750, "Documento generico com camada de texto suficiente");
                }

                // Ordem interna oposta a ordem visual.
                escrever(conteudo, 300, 650, "DIREITA");
                escrever(conteudo, 50, 650, "ESQUERDA");
            }

            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private void escrever(PDPageContentStream conteudo, float x, float y, String texto)
            throws IOException {
        conteudo.beginText();
        conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        conteudo.newLineAtOffset(x, y);
        conteudo.showText(texto);
        conteudo.endText();
    }
}
