package com.criati.criati.engine.model;

public class DocumentoContexto {

    private String nomeArquivo;
    private String texto;

    public DocumentoContexto(String nomeArquivo, String texto) {
        this.nomeArquivo = nomeArquivo;
        this.texto = texto;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getTexto() {
        return texto;
    }
}