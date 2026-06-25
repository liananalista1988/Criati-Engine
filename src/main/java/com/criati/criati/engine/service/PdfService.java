package com.criati.criati.engine.service;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.PdfTextoResponse;

@Service
public class PdfService {

    public PdfTextoResponse extrairTexto(MultipartFile arquivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(arquivo.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(documento);

            return new PdfTextoResponse(
                    arquivo.getOriginalFilename(),
                    texto.length(),
                    texto
            );
        }
    }
}