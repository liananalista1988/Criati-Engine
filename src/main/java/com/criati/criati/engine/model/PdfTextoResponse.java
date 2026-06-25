package com.criati.criati.engine.model;

public class PdfTextoResponse {

    private String nomeArquivo;
    private int quantidadeCaracteres;
    private String texto;

    public PdfTextoResponse(String nomeArquivo, int quantidadeCaracteres, String texto) {
        this.nomeArquivo = nomeArquivo;
        this.quantidadeCaracteres = quantidadeCaracteres;
        this.texto = texto;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public int getQuantidadeCaracteres() {
        return quantidadeCaracteres;
    }

    public String getTexto() {
        return texto;
    }
}