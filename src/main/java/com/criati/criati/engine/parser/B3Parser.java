package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class B3Parser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = contexto.getTexto().toUpperCase();

        return t.contains("RELATÓRIO MENSAL CONSOLIDADO")
                || t.contains("RELATORIO MENSAL CONSOLIDADO")
                || t.contains("INVESTIDOR.B3.COM.BR")
                || t.contains("PROVENTOS RECEBIDOS")
                || t.contains("POSIÇÃO - FII");
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {
        String texto = contexto.getTexto();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("B3");
        extrato.setConta("B3");

        extrato.setCompetencia(extrairCompetencia(texto));

        extrato.setNomeFundo(extrairNomeFundo(texto));
        extrato.setCnpjFundo(null);

        extrato.setAdministrador(extrair(texto, "(BB\\s+BANCO\\s+DE\\s+INVESTIMENTO\\s+S/A)"));

        extrato.setSaldoInicial(null);
        extrato.setAplicacoes("0,00");
        extrato.setResgates("0,00");
        extrato.setRendimentos(extrairRendimentos(texto));
        extrato.setSaldoFinal(extrairSaldoFinal(texto));

        extrato.setRentabilidadeMes(null);
        extrato.setRentabilidadeAno(null);
        extrato.setRentabilidade12Meses(null);

        return extrato;
    }

    private String extrairCompetencia(String texto) {
        String comp = extrair(texto, "Data:\\s*(\\d{2}/20\\d{2})");

        if (comp != null) {
            return comp;
        }

        Pattern p = Pattern.compile(
                "Proventos\\s+recebidos\\s+-\\s+([A-Za-zçÇ]+)\\s+de\\s+(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = p.matcher(texto);

        if (m.find()) {
            return mesPorExtenso(m.group(1)) + "/" + m.group(2);
        }

        return null;
    }

    private String extrairNomeFundo(String texto) {
        String nome = extrair(texto, "(RBRD11\\s+-\\s+RB\\s+CAPITAL.*?RESP\\s+LTDA\\.)");

        if (nome != null) {
            return nome;
        }

        return extrair(texto, "([A-Z0-9]{4}11\\s+-\\s+.*?FII.*?LTDA\\.)");
    }

    private String extrairSaldoFinal(String texto) {
        String valor = extrair(texto, "Total\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})");

        if (valor != null) {
            return valor;
        }

        return extrair(texto, "Valor\\s+Atualizado.*?R\\$\\s*([0-9\\.]+,[0-9]{2})");
    }

    private String extrairRendimentos(String texto) {
        Pattern p = Pattern.compile(
                "Proventos\\s+recebidos.*?Total\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = p.matcher(texto);

        if (m.find()) {
            return m.group(1).trim();
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
            if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                return matcher.group(1).trim().replaceAll("\\s+", " ");
            }

            return matcher.group().trim().replaceAll("\\s+", " ");
        }

        return null;
    }
}