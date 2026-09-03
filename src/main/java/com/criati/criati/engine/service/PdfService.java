package com.criati.criati.engine.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.exception.LeituraPdfException;
import com.criati.criati.engine.model.PdfTextoResponse;

@Service
public class PdfService {

    /**
     * Quantidade minima de caracteres uteis que a extracao nativa do PDFBox
     * precisa retornar para considerarmos que o PDF tem uma camada de texto
     * de verdade. Abaixo disso, tratamos o PDF como "sem texto" (ex.: PDF
     * gerado a partir de uma imagem ou com o texto convertido em desenhos
     * vetoriais, como e o caso de arquivos gerados por alguns drivers de
     * "imprimir em PDF") e caimos para OCR.
     */
    private static final int MINIMO_CARACTERES_TEXTO_NATIVO = 30;

    private static final int DPI_RENDERIZACAO_OCR = 300;

    public PdfTextoResponse extrairTexto(MultipartFile arquivo) throws IOException {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new LeituraPdfException(
                    "Nenhum arquivo foi enviado (ou o arquivo esta vazio). Selecione um PDF valido.");
        }

        byte[] bytes = arquivo.getBytes();

        try (PDDocument documento = Loader.loadPDF(bytes)) {

            String textoNativo = extrairTextoNativo(documento);
            String textoFinal = textoNativo;

            if (ehExtratoMensalBnb(textoNativo)) {
                String textoOrdenado = extrairTextoNativoOrdenado(documento);

                if (textoOrdenado != null && !textoOrdenado.isBlank()) {
                    textoFinal = textoOrdenado;
                }
            }

            boolean textoNativoInsuficiente =
                    textoNativo == null || textoNativo.trim().length() < MINIMO_CARACTERES_TEXTO_NATIVO;

            if (textoNativoInsuficiente) {
                String textoOcr = tentarOcr(documento);

                if (textoOcr != null && !textoOcr.isBlank()) {
                    textoFinal = textoOcr;
                }
            }

            if (textoFinal == null || textoFinal.isBlank()) {
                throw new LeituraPdfException(
                        "Nao foi possivel extrair texto do PDF \"" + arquivo.getOriginalFilename() + "\". "
                        + "O arquivo nao possui uma camada de texto pesquisavel (provavelmente foi gerado a partir "
                        + "de uma imagem/digitalizacao, com o conteudo desenhado como grafico vetorial) e a "
                        + "tentativa de leitura via OCR nao retornou conteudo. Verifique se o arquivo nao esta "
                        + "corrompido, se o OCR (Tesseract) esta instalado no servidor, ou tente reexportar o PDF "
                        + "com texto pesquisavel.");
            }

            return new PdfTextoResponse(
                    arquivo.getOriginalFilename(),
                    textoFinal.length(),
                    textoFinal
            );

        } catch (LeituraPdfException e) {
            throw e;
        } catch (Exception e) {
            throw new LeituraPdfException(
                    "Falha ao abrir/ler o PDF \"" + arquivo.getOriginalFilename() + "\": " + e.getMessage(), e);
        }
    }

    private String extrairTextoNativo(PDDocument documento) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(documento);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * O layout "Sistema Fundos de Investimento / Extrato Mensal" do BNB
     * grava as colunas fora da ordem visual no fluxo interno do PDF. A
     * segunda leitura por posicao fica restrita a esse layout para preservar
     * a extracao historica dos documentos do BB, da Caixa e da B3.
     */
    private String extrairTextoNativoOrdenado(PDDocument documento) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(documento);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean ehExtratoMensalBnb(String texto) {
        if (texto == null) {
            return false;
        }

        String normalizado = texto.toUpperCase(java.util.Locale.ROOT);

        return normalizado.contains("BANCO DO NORDESTE")
                && normalizado.contains("SISTEMA FUNDOS DE INVESTIMENTO")
                && normalizado.contains("EXTRATO MENSAL");
    }

    /**
     * Renderiza cada pagina do PDF como imagem e tenta reconhecer o texto
     * usando o Tesseract (instalado no ambiente/Docker via
     * "tesseract-ocr" + "tesseract-ocr-por"). Se o binario do Tesseract
     * nao estiver disponivel, retorna null silenciosamente para que o
     * chamador reporte o erro de forma clara ao usuario.
     */
    private String tentarOcr(PDDocument documento) {
        try {
            PDFRenderer renderer = new PDFRenderer(documento);
            StringBuilder textoCompleto = new StringBuilder();

            int quantidadePaginas = documento.getNumberOfPages();

            for (int pagina = 0; pagina < quantidadePaginas; pagina++) {
                BufferedImage imagem = renderer.renderImageWithDPI(pagina, DPI_RENDERIZACAO_OCR, ImageType.GRAY);

                File arquivoTemporario = File.createTempFile("criati-ocr-", ".png");

                try {
                    ImageIO.write(imagem, "png", arquivoTemporario);

                    String textoPagina = executarTesseract(arquivoTemporario);

                    if (textoPagina != null) {
                        textoCompleto.append(textoPagina).append("\n");
                    }
                } finally {
                    Files.deleteIfExists(arquivoTemporario.toPath());
                }
            }

            return textoCompleto.toString();

        } catch (Exception e) {
            return null;
        }
    }

    private String executarTesseract(File imagem) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "tesseract", imagem.getAbsolutePath(), "stdout", "-l", "por", "--psm", "6"
        );
        pb.redirectErrorStream(false);

        Process processo = pb.start();

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (var entrada = processo.getInputStream()) {
            entrada.transferTo(saida);
        }

        boolean finalizou = processo.waitFor(60, TimeUnit.SECONDS);

        if (!finalizou) {
            processo.destroyForcibly();
            return null;
        }

        if (processo.exitValue() != 0) {
            return null;
        }

        return saida.toString("UTF-8");
    }
}
