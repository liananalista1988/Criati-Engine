package com.criati.criati.engine.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.PdfTextoResponse;
import com.criati.criati.engine.service.PdfService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping(value = "/extrair-texto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PdfTextoResponse extrairTexto(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return pdfService.extrairTexto(arquivo);
    }
}