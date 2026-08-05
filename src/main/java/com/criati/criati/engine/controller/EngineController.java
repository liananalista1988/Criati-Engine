package com.criati.criati.engine.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
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
     * cru mesmo sem parser), aqui um corpo null nao serve para nada: o
     * consumidor gravaria a posicao vazia. Entao falha alto.
     */
    @PostMapping(value = "/extrato-investimento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object extratoInvestimento(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        DocumentoProcessadoResponse resposta = engineService.processar(arquivo);

        Object dados = resposta.getDados();

        if (dados == null) {
            throw new ExtratoIncompletoException(
                    "Nenhum parser reconheceu o documento \"" + resposta.getNomeArquivo() + "\""
                            + " (tipo detectado: " + resposta.getTipoDocumento()
                            + ", instituicao detectada: " + resposta.getInstituicao() + ")."
                            + " Nenhuma posicao foi retornada.",
                    List.of());
        }

        return dados;
    }
    
    
}