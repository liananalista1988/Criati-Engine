package com.criati.criati.engine.model;

public class DocumentoProcessadoResponse {

    private String nomeArquivo;
    private String tipoDocumento;
    private String instituicao;
    private String competencia;
    private int quantidadeCaracteres;
    private String texto;
    private Object dados;

    public DocumentoProcessadoResponse(String nomeArquivo, String tipoDocumento, String instituicao,
                                       String competencia, int quantidadeCaracteres, String texto, Object dados) {
        this.nomeArquivo = nomeArquivo;
        this.tipoDocumento = tipoDocumento;
        this.instituicao = instituicao;
        this.competencia = competencia;
        this.quantidadeCaracteres = quantidadeCaracteres;
        this.texto = texto;
        this.dados = dados;
    }

    public String getNomeArquivo() { return nomeArquivo; }
    public String getTipoDocumento() { return tipoDocumento; }
    public String getInstituicao() { return instituicao; }
    public String getCompetencia() { return competencia; }
    public int getQuantidadeCaracteres() { return quantidadeCaracteres; }
    public String getTexto() { return texto; }
    public Object getDados() { return dados; }
}