package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class BancoBrasilParser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = contexto.getTexto().toUpperCase();

        return t.contains("CONSULTAS - INVESTIMENTOS FUNDOS - MENSAL")
                && t.contains("MÊS/ANO REFERÊNCIA")
                && t.contains("RESUMO DO MÊS");
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {
        String texto = contexto.getTexto();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("BB");
        extrato.setConta(extrair(texto, "Conta\\s+(\\d{4,6}-\\d)"));
        extrato.setCompetencia(extrairCompetencia(texto));

        String bloco = extrairPrimeiroBlocoFundo(texto);

        if (bloco == null) {
            bloco = texto;
        }

        extrato.setNomeFundo(extrair(bloco, "(BB\\s+.*?)-\\s+CNPJ:"));
        extrato.setCnpjFundo(extrair(bloco, "CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setSaldoInicial(extrair(bloco, "SALDO\\s+ANTERIOR\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setAplicacoes(extrair(bloco, "APLICAÇÕES\\s*\\(\\+\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setResgates(extrair(bloco, "RESGATES\\s*\\(-\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setRendimentos(extrair(bloco, "RENDIMENTO\\s+BRUTO\\s*\\(\\+\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setSaldoFinal(extrair(bloco, "SALDO\\s+ATUAL\\s*=\\s*([\\-]?[0-9\\.]+,[0-9]{2})"));

        extrato.setRentabilidadeMes(extrair(bloco, "No\\s+mês\\s+([\\-]?[0-9]+,[0-9]+)"));
        extrato.setRentabilidadeAno(extrair(bloco, "No\\s+ano\\s+([\\-]?[0-9]+,[0-9]+)"));
        extrato.setRentabilidade12Meses(extrair(bloco, "Últimos\\s+12\\s+meses\\s+([\\-]?[0-9]+,[0-9]+)"));

        return extrato;
    }

    private String extrairPrimeiroBlocoFundo(String texto) {
        Pattern pattern = Pattern.compile(
                "(BB\\s+.*?-\\s+CNPJ:\\s*\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}.*?)(?=\\nBB\\s+.*?-\\s+CNPJ:|Transação efetuada|Serviço de Atendimento|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String extrairCompetencia(String texto) {
        Pattern pattern = Pattern.compile(
                "Mês/ano\\s+referência\\s+([A-Za-zçÇ]+)/?(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return mesPorExtenso(matcher.group(1)) + "/" + matcher.group(2);
        }

        return null;
    }

    private String mesPorExtenso(String mes) {
        String m = mes.toUpperCase();

        if (m.contains("JANEIRO")) return "01";
        if (m.contains("FEVEREIRO")) return "02";
        if (m.contains("MARÇO") || m.contains("MARCO")) return "03";
        if (m.contains("ABRIL")) return "04";
        if (m.contains("MAIO")) return "05";
        if (m.contains("JUNHO")) return "06";
        if (m.contains("JULHO")) return "07";
        if (m.contains("AGOSTO")) return "08";
        if (m.contains("SETEMBRO")) return "09";
        if (m.contains("OUTUBRO")) return "10";
        if (m.contains("NOVEMBRO")) return "11";
        if (m.contains("DEZEMBRO")) return "12";

        return null;
    }

    private String extrair(String texto, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("\\s+", " ");
        }

        return null;
    }
}