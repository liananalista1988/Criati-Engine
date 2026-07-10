package com.criati.criati.engine.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.PdfTextoResponse;

@Service
public class PdfService {

    private static final int MINIMO_CARACTERES_VALIDOS = 100;

    public PdfTextoResponse extrairTexto(MultipartFile arquivo) throws IOException {

        byte[] bytes = arquivo.getBytes();

        /*
         * PRIMEIRA TENTATIVA:
         * mantém exatamente o funcionamento atual com PDFBox.
         */
        String texto = extrairComPdfBox(bytes);

        /*
         * SEGUNDA TENTATIVA:
         * só executa OCR quando o método atual não conseguir
         * extrair um texto minimamente válido.
         */
        if (!textoValido(texto)) {
            texto = extrairComOcr(bytes);
        }

        if (texto == null) {
            texto = "";
        }

        return new PdfTextoResponse(
                arquivo.getOriginalFilename(),
                texto.length(),
                texto
        );
    }

    private String extrairComPdfBox(byte[] bytes) throws IOException {

        try (PDDocument documento = Loader.loadPDF(bytes)) {

            PDFTextStripper stripper = new PDFTextStripper();

            return stripper.getText(documento);
        }
    }

    private boolean textoValido(String texto) {

        if (texto == null || texto.isBlank()) {
            return false;
        }

        String textoLimpo = texto
                .replaceAll("\\s+", "")
                .trim();

        if (textoLimpo.length() < MINIMO_CARACTERES_VALIDOS) {
            return false;
        }

        /*
         * Confirma que o PDFBox realmente encontrou palavras,
         * e não apenas caracteres soltos ou lixo do PDF.
         */
        long quantidadeLetras = textoLimpo
                .chars()
                .filter(Character::isLetter)
                .count();

        return quantidadeLetras >= 30;
    }

    private String extrairComOcr(byte[] bytes) throws IOException {

        Path pastaTemporaria = Files.createTempDirectory("criati-ocr-");

        StringBuilder textoCompleto = new StringBuilder();

        try (PDDocument documento = Loader.loadPDF(bytes)) {

            PDFRenderer renderer = new PDFRenderer(documento);

            for (
                    int pagina = 0;
                    pagina < documento.getNumberOfPages();
                    pagina++
            ) {

                BufferedImage imagem = renderer.renderImageWithDPI(
                        pagina,
                        300,
                        ImageType.RGB
                );

                Path caminhoImagem = pastaTemporaria.resolve(
                        "pagina-" + pagina + ".png"
                );

                ImageIO.write(
                        imagem,
                        "png",
                        caminhoImagem.toFile()
                );

                String textoPagina = executarTesseract(caminhoImagem);

                textoCompleto
                        .append(textoPagina)
                        .append(System.lineSeparator());
            }

        } finally {
            excluirPastaTemporaria(pastaTemporaria);
        }

        return textoCompleto.toString();
    }

    private String executarTesseract(Path caminhoImagem) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder(
                "tesseract",
                caminhoImagem.toAbsolutePath().toString(),
                "stdout",
                "-l",
                "por",
                "--psm",
                "6"
        );

        processBuilder.redirectErrorStream(true);

        Process processo = processBuilder.start();

        try {

            String retorno = new String(
                    processo.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            int codigoSaida = processo.waitFor();

            if (codigoSaida != 0) {
                throw new IOException(
                        "O OCR falhou. Código: "
                                + codigoSaida
                                + ". Retorno: "
                                + retorno
                );
            }

            return retorno;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "O processamento OCR foi interrompido.",
                    e
            );
        }
    }

    private void excluirPastaTemporaria(Path pasta) {

        if (pasta == null || !Files.exists(pasta)) {
            return;
        }

        try (var caminhos = Files.walk(pasta)) {

            caminhos
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(caminho -> {
                        try {
                            Files.deleteIfExists(caminho);
                        } catch (IOException ignored) {
                            // Apenas limpeza de arquivos temporários.
                        }
                    });

        } catch (IOException ignored) {
            // Não impede o retorno do extrato.
        }
    }
}