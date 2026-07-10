package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class BnbExtratoMensalParser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = normalizar(contexto.getTexto()).toUpperCase();

        boolean ehBnb = t.contains("BANCO DO NORDESTE") || t.contains("BNB SOBERANO") || t.contains("MOVIMENTACOES BNB");

        boolean ehLayoutExtratoMensal = t.contains("SISTEMA FUNDOS DE INVESTIMENTO")
                || (t.contains("EXTRATO MENSAL") && t.contains("SANTANDER SECURITIES SERVICES"));

        boolean naoEhOutroLayout = !t.contains("EXTRATO CONSOLIDADO")
                && !t.contains("SALDO BRUTO ANTERIOR")
                && !t.contains("SALDO BRUTO FINAL");

        return ehBnb && ehLayoutExtratoMensal && naoEhOutroLayout;
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {
        String texto = normalizar(contexto.getTexto());

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("BNB");
        extrato.setConta(extrairConta(texto));
        extrato.setCompetencia(extrairCompetencia(texto));

        extrato.setNomeFundo(extrair(texto,
                "MOVIMENTA[CÇ][OÕ]ES\\s+(.+?)\\s*-\\s*CNPJ"));

        extrato.setCnpjFundo(extrair(texto,
                "MOVIMENTA[CÇ][OÕ]ES.*?CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setAdministrador(extrair(texto,
                "ADMINISTRADOR FIDUCI[ÁA]RIO:\\s*(.*?)\\s*CNPJ:"));

        extrato.setCnpjAdministrador(extrair(texto,
                "ADMINISTRADOR FIDUCI[ÁA]RIO:.*?CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setRentabilidadeMes(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidadeAno(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidade12Meses(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)"));

        extrato.setSaldoInicial(extrair(texto,
                "SALDO\\s+ANTERIOR\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        extrato.setSaldoFinal(extrair(texto,
                "\\.?\\s*SALDO\\s+FINAL\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        String cabecalhoResumo = "TOTAL\\s+APLICA[CÇ][OÕ]ES\\s+TOTAL\\s+RESGATES\\s+REND\\.?\\s*BRUTO\\s+MENSAL\\s+I\\.?R\\.?\\s+FEDERAL";

        extrato.setAplicacoes(extrair(texto,
                cabecalhoResumo + "\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setResgates(extrair(texto,
                cabecalhoResumo + "\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setRendimentos(extrair(texto,
                cabecalhoResumo + "\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        return extrato;
    }

    private String extrairConta(String texto) {
        String conta = extrair(texto, "\\d{2,3}\\s*-\\s*[A-ZÀ-Ú\\s]+?\\s+(\\d{5,9}-\\d)\\s+\\d+");

        if (conta != null) {
            return conta;
        }

        return extrair(texto, "(\\d{6,9}-\\d)");
    }

    private String extrairCompetencia(String texto) {
        Pattern padrao = Pattern.compile(
                "\\d{2}/\\d{2}/20\\d{2}\\s+(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = padrao.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1) + "/" + matcher.group(2);
        }

        Pattern padraoRotulo = Pattern.compile(
                "M[eê]s/Ano\\s+(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherRotulo = padraoRotulo.matcher(texto);

        if (matcherRotulo.find()) {
            return matcherRotulo.group(1) + "/" + matcherRotulo.group(2);
        }

        return null;
    }

    private String extrair(String texto, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String valor = matcher.group(i);

                if (valor != null && !valor.isBlank()) {
                    return limpar(valor);
                }
            }

            return limpar(matcher.group());
        }

        return null;
    }

    private String normalizar(String valor) {
        if (valor == null) return "";

        return valor
                .replace("\u00A0", " ")
                .replace("\r", "\n");
    }

    private String limpar(String valor) {
        if (valor == null) return null;

        return valor
                .replace("\u00A0", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}