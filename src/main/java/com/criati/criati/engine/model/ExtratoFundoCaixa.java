package com.criati.criati.engine.model;

public class ExtratoFundoCaixa {

    // Dados do arquivo
    private String conta;

    // Fundo
    private String nomeFundo;
    private String cnpjFundo;

    // Administrador
    private String administrador;
    private String cnpjAdministrador;

    // Período
    private String dataInicio;
    private String dataFim;

    // Rentabilidades
    private String rentabilidadeMes;
    private String rentabilidadeAno;
    private String rentabilidade12Meses;

    // Valores
    private String saldoInicial;
    private String aplicacoes;
    private String resgates;
    private String rendimentoBruto;
    private String saldoFinal;

    // =========================
    // GETTERS E SETTERS
    // =========================

    public String getConta() {
        return conta;
    }

    public void setConta(String conta) {
        this.conta = conta;
    }

    public String getNomeFundo() {
        return nomeFundo;
    }

    public void setNomeFundo(String nomeFundo) {
        this.nomeFundo = nomeFundo;
    }

    public String getCnpjFundo() {
        return cnpjFundo;
    }

    public void setCnpjFundo(String cnpjFundo) {
        this.cnpjFundo = cnpjFundo;
    }

    public String getAdministrador() {
        return administrador;
    }

    public void setAdministrador(String administrador) {
        this.administrador = administrador;
    }

    public String getCnpjAdministrador() {
        return cnpjAdministrador;
    }

    public void setCnpjAdministrador(String cnpjAdministrador) {
        this.cnpjAdministrador = cnpjAdministrador;
    }

    public String getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(String dataInicio) {
        this.dataInicio = dataInicio;
    }

    public String getDataFim() {
        return dataFim;
    }

    public void setDataFim(String dataFim) {
        this.dataFim = dataFim;
    }

    public String getRentabilidadeMes() {
        return rentabilidadeMes;
    }

    public void setRentabilidadeMes(String rentabilidadeMes) {
        this.rentabilidadeMes = rentabilidadeMes;
    }

    public String getRentabilidadeAno() {
        return rentabilidadeAno;
    }

    public void setRentabilidadeAno(String rentabilidadeAno) {
        this.rentabilidadeAno = rentabilidadeAno;
    }

    public String getRentabilidade12Meses() {
        return rentabilidade12Meses;
    }

    public void setRentabilidade12Meses(String rentabilidade12Meses) {
        this.rentabilidade12Meses = rentabilidade12Meses;
    }

    public String getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(String saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public String getAplicacoes() {
        return aplicacoes;
    }

    public void setAplicacoes(String aplicacoes) {
        this.aplicacoes = aplicacoes;
    }

    public String getResgates() {
        return resgates;
    }

    public void setResgates(String resgates) {
        this.resgates = resgates;
    }

    public String getRendimentoBruto() {
        return rendimentoBruto;
    }

    public void setRendimentoBruto(String rendimentoBruto) {
        this.rendimentoBruto = rendimentoBruto;
    }

    public String getSaldoFinal() {
        return saldoFinal;
    }

    public void setSaldoFinal(String saldoFinal) {
        this.saldoFinal = saldoFinal;
    }
}