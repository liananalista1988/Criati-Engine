package com.criati.criati.engine.model;

public class ExtratoInvestimento {

    private String conta;
    private String instituicao;
    private String competencia;

    private String nomeFundo;
    private String cnpjFundo;
    private String administrador;
    private String cnpjAdministrador;

    private String dataInicio;
    private String dataFim;

    private String rentabilidadeMes;
    private String rentabilidadeAno;
    private String rentabilidade12Meses;

    private String saldoInicial;
    private String aplicacoes;
    private String resgates;
    private String rendimentos;
    private String saldoFinal;

    public String getConta() { return conta; }
    public void setConta(String conta) { this.conta = conta; }

    public String getInstituicao() { return instituicao; }
    public void setInstituicao(String instituicao) { this.instituicao = instituicao; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getNomeFundo() { return nomeFundo; }
    public void setNomeFundo(String nomeFundo) { this.nomeFundo = nomeFundo; }

    public String getCnpjFundo() { return cnpjFundo; }
    public void setCnpjFundo(String cnpjFundo) { this.cnpjFundo = cnpjFundo; }

    public String getAdministrador() { return administrador; }
    public void setAdministrador(String administrador) { this.administrador = administrador; }

    public String getCnpjAdministrador() { return cnpjAdministrador; }
    public void setCnpjAdministrador(String cnpjAdministrador) { this.cnpjAdministrador = cnpjAdministrador; }

    public String getDataInicio() { return dataInicio; }
    public void setDataInicio(String dataInicio) { this.dataInicio = dataInicio; }

    public String getDataFim() { return dataFim; }
    public void setDataFim(String dataFim) { this.dataFim = dataFim; }

    public String getRentabilidadeMes() { return rentabilidadeMes; }
    public void setRentabilidadeMes(String rentabilidadeMes) { this.rentabilidadeMes = rentabilidadeMes; }

    public String getRentabilidadeAno() { return rentabilidadeAno; }
    public void setRentabilidadeAno(String rentabilidadeAno) { this.rentabilidadeAno = rentabilidadeAno; }

    public String getRentabilidade12Meses() { return rentabilidade12Meses; }
    public void setRentabilidade12Meses(String rentabilidade12Meses) { this.rentabilidade12Meses = rentabilidade12Meses; }

    public String getSaldoInicial() { return saldoInicial; }
    public void setSaldoInicial(String saldoInicial) { this.saldoInicial = saldoInicial; }

    public String getAplicacoes() { return aplicacoes; }
    public void setAplicacoes(String aplicacoes) { this.aplicacoes = aplicacoes; }

    public String getResgates() { return resgates; }
    public void setResgates(String resgates) { this.resgates = resgates; }

    public String getRendimentos() { return rendimentos; }
    public void setRendimentos(String rendimentos) { this.rendimentos = rendimentos; }

    public String getSaldoFinal() { return saldoFinal; }
    public void setSaldoFinal(String saldoFinal) { this.saldoFinal = saldoFinal; }
}