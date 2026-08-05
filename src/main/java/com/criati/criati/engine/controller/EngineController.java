package com.criati.criati.engine.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.DocumentoProcessadoResponse;
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

    /**
     * Diferente de /engine/processar (que e diagnostico e devolve o texto
     * cru mesmo sem parser), aqui HTTP 200 significa posicao completa: se
     * qualquer campo obrigatorio do layout faltar, a resposta e 422. O
     * consumidor grava direto o que recebe daqui.
     */
    @PostMapping(value = "/extrato-investimento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object extratoInvestimento(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return engineService.processar(arquivo, true).getDados();
    }

}