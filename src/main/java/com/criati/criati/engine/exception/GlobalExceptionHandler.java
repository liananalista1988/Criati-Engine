package com.criati.criati.engine.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LeituraPdfException.class)
    public ResponseEntity<Map<String, Object>> tratarLeituraPdf(LeituraPdfException ex) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, "LEITURA_PDF_FALHOU", ex.getMessage());
    }

    @ExceptionHandler(ExtratoIncompletoException.class)
    public ResponseEntity<Map<String, Object>> tratarExtratoIncompleto(ExtratoIncompletoException ex) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("sucesso", false);
        corpo.put("codigo", "EXTRATO_INCOMPLETO");
        corpo.put("mensagem", ex.getMessage());
        corpo.put("camposFaltando", ex.getCamposFaltando());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(corpo);
    }

    @ExceptionHandler({MultipartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> tratarMultipart(Exception ex) {
        return responder(HttpStatus.BAD_REQUEST, "ARQUIVO_INVALIDO",
                "Não foi possível ler o arquivo enviado. Verifique se é um PDF válido e tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarGenerico(Exception ex) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Ocorreu um erro inesperado ao processar o documento: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> responder(HttpStatus status, String codigo, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("sucesso", false);
        corpo.put("codigo", codigo);
        corpo.put("mensagem", mensagem);

        return ResponseEntity.status(status).body(corpo);
    }
}