package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class BnbParser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = contexto.getTexto().toUpperCase();

        return t.contains("EXTRATO CONSOLIDADO")
                && (
                    t.contains("BANCO DO NORDESTE")
                    || t.contains("BNB SOBERANO")
                    || t.contains("MOVIMENTACOES BNB")
                );
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {
        String texto = contexto.getTexto();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("BNB");
        extrato.setConta(extrair(texto, "CONTA\\s+(\\d{3}\\.\\d{3}-\\d)"));

        extrato.setCompetencia(extrairCompetencia(texto));

        extrato.setNomeFundo(extrairNomeFundo(texto));
        extrato.setCnpjFundo(extrair(texto, "MOVIMENTACOES\\s+BNB\\s+SOBERANO\\s+FIF\\s+-\\s+CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setAdministrador(extrair(texto, "ADMINISTRADOR FIDUCIARIO:\\s*(.*?)\\s+CNPJ:"));
        extrato.setCnpjAdministrador(extrair(texto, "ADMINISTRADOR FIDUCIARIO:.*?CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setRentabilidadeMes(extrair(texto, "BNB\\s+SOBERANO\\s+FIF\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidadeAno(extrair(texto, "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidade12Meses(extrair(texto, "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)"));

        extrato.setSaldoInicial(extrair(texto, "SALDO\\s+INICIAL\\s+[0-9\\.]+,[0-9]+\\s+[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setSaldoFinal(extrair(texto, "SALDO\\s+FINAL\\s+[0-9\\.]+,[0-9]+\\s+[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        extrato.setAplicacoes(extrair(texto, "APLICACOES\\s+NO\\s+MES\\s+RESGATES\\s+NO\\s+MES\\s+REND\\.BRUTO\\s+MENSAL\\s+I\\.R\\.\\s+FEDERAL\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setResgates(extrair(texto, "APLICACOES\\s+NO\\s+MES\\s+RESGATES\\s+NO\\s+MES\\s+REND\\.BRUTO\\s+MENSAL\\s+I\\.R\\.\\s+FEDERAL\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setRendimentos(extrair(texto, "APLICACOES\\s+NO\\s+MES\\s+RESGATES\\s+NO\\s+MES\\s+REND\\.BRUTO\\s+MENSAL\\s+I\\.R\\.\\s+FEDERAL\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        return extrato;
    }

    private String extrairCompetencia(String texto) {

        Pattern referencia = Pattern.compile(
                "REFERENCIA:\\s*([A-ZÇ]+)/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherReferencia = referencia.matcher(texto);

        if (matcherReferencia.find()) {
            String mes = matcherReferencia.group(1);
            String ano = matcherReferencia.group(2);

            return mesPorExtenso(mes) + "/" + ano;
        }

        Pattern mesEmissao = Pattern.compile(
                "M[eê]s:\\s*([A-Za-zçÇ]+)/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherMes = mesEmissao.matcher(texto);

        if (matcherMes.find()) {
            String mes = matcherMes.group(1);
            String ano = matcherMes.group(2);

            return mesPorExtenso(mes) + "/" + ano;
        }

        return null;
    }

    private String extrairNomeFundo(String texto) {
        String nome = extrair(texto, ">\\s*MOVIMENTACOES\\s+(BNB\\s+SOBERANO\\s+FIF)\\s+-\\s+CNPJ:");

        if (nome != null) {
            return nome;
        }

        return extrair(texto, "(BNB\\s+SOBERANO\\s+FIF)");
    }

    private String mesPorExtenso(String mes) {
        String m = mes.toUpperCase();

        if (m.contains("JANEIRO")) return "01";
        if (m.contains("FEVEREIRO")) return "02";
        if (m.contains("MARCO") || m.contains("MARÇO")) return "03";
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
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String valor = matcher.group(i);

                if (valor != null && !valor.isBlank()) {
                    return valor.trim().replaceAll("\\s+", " ");
                }
            }

            return matcher.group().trim().replaceAll("\\s+", " ");
        }

        return null;
    }
}