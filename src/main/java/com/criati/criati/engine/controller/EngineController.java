package com.criati.criati.engine.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.DocumentoProcessadoResponse;
import com.criati.criati.engine.model.ExtratoInvestimento;
import com.criati.criati.engine.service.EngineService;

@RestController
@RequestMapping("/engine")
public class EngineController {

    private final EngineService engineService;

    public EngineController(EngineService engineService) {
        this.engineService = engineService;
    }

    @PostMapping(value = "/processar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentoProcessadoResponse processar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return engineService.processar(arquivo);
    }

    @PostMapping(value = "/extrato-investimento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtratoInvestimento extratoInvestimento(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        DocumentoProcessadoResponse resposta = engineService.processar(arquivo);

        if (resposta.getDados() instanceof ExtratoInvestimento extrato) {
            return extrato;
        }

        return null;
    }
}