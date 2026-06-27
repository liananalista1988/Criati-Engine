package com.criati.criati.engine.parser;

import java.util.ArrayList;
import java.util.List;
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
        List<ExtratoInvestimento> lista = processarTodos(contexto);

        if (lista.isEmpty()) {
            return new ExtratoInvestimento();
        }

        return lista.get(0);
    }

    public List<ExtratoInvestimento> processarTodos(DocumentoContexto contexto) {
        String texto = normalizar(contexto.getTexto());

        String conta = extrair(texto, "Conta\\s+(\\d{4,6}-[\\dXx])");
        String competencia = extrairCompetencia(texto);

        List<String> blocos = extrairBlocosFundos(texto);
        List<ExtratoInvestimento> extratos = new ArrayList<>();

        for (String bloco : blocos) {
            ExtratoInvestimento extrato = new ExtratoInvestimento();

            extrato.setInstituicao("BB");
            extrato.setConta(conta);
            extrato.setCompetencia(competencia);

            String nomeOriginal = extrair(bloco,
                    "^\\s*(.*?)\\s*-\\s*CNPJ:");

            extrato.setNomeFundo(padronizarNomeFundo(nomeOriginal));

            extrato.setCnpjFundo(extrair(bloco,
                    "CNPJ:\\s*(\\d{1,2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

            extrato.setSaldoInicial(extrair(bloco,
                    "SALDO\\s+ANTERIOR\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setAplicacoes(extrair(bloco,
                    "APLICAÇÕES\\s*\\(\\+\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setResgates(extrair(bloco,
                    "RESGATES\\s*\\(-\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setRendimentos(extrair(bloco,
                    "RENDIMENTO\\s+BRUTO\\s*\\([\\+\\-]\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setSaldoFinal(extrair(bloco,
                    "SALDO\\s+ATUAL\\s*=\\s*([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setRentabilidadeMes(extrair(bloco,
                    "No\\s+m[eê]s\\s+([\\-]?[0-9]+,[0-9]+)"));

            extrato.setRentabilidadeAno(extrair(bloco,
                    "No\\s+ano\\s+([\\-]?[0-9]+,[0-9]+)"));

            extrato.setRentabilidade12Meses(extrair(bloco,
                    "Últimos\\s+12\\s+meses\\s+([\\-]?[0-9]+,[0-9]+)"));

            extratos.add(extrato);
        }

        return extratos;
    }

    private List<String> extrairBlocosFundos(String texto) {
        List<Integer> inicios = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "(?m)^\\s*[^\\n]+?\\s*-\\s*CNPJ:\\s*\\d{1,2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(texto);

        while (matcher.find()) {
            inicios.add(matcher.start());
        }

        List<String> blocos = new ArrayList<>();

        for (int i = 0; i < inicios.size(); i++) {
            int inicio = inicios.get(i);
            int fim = (i + 1 < inicios.size()) ? inicios.get(i + 1) : texto.length();

            blocos.add(texto.substring(inicio, fim));
        }

        return blocos;
    }

    private String extrairCompetencia(String texto) {
        Pattern pattern = Pattern.compile(
                "M[eê]s/ano\\s+referência\\s+([A-Za-zçÇ]+)/?(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return mesPorExtenso(matcher.group(1)) + "/" + matcher.group(2);
        }

        return null;
    }

    private String padronizarNomeFundo(String nome) {
        String n = limpar(nome);

        if (n == null) return null;

        String u = n.toUpperCase();

        if (u.equals("INSTITUCIONAL RF")) {
            return "BB INSTITUCIONAL RF";
        }

        if (u.equals("PREVID RF IMA-B 5")) {
            return "BB PREVID RF IMA-B5";
        }

        if (u.equals("BB PREVID RF IMA-B")) {
            return "BB IMA-B RF";
        }

        if (u.equals("AÇÕES SELEÇÃO FATOR")) {
            return "BB AÇÕES SELEÇÃO FATOR";
        }

        if (u.equals("MM JUROS E MOEDAS")) {
            return "BB MM JUROS E MOEDAS";
        }

        if (u.equals("AÇÕES DIVIDENDOS MIDCAPS")) {
            return "BB AÇÕES DIVIDENDOS MIDCAPS";
        }

        if (u.equals("AÇÕES BOLSA AMERICAN") || u.equals("AÇÕES BOLSA AMERICANA")) {
            return "BB AÇÕES BOLSA AMERICANA";
        }

        if (u.equals("AÇÕES GLOBAIS ATIVO")) {
            return "BB AÇÕES GLOBAIS ATIVO";
        }

        if (!u.startsWith("BB ")) {
            return "BB " + n;
        }

        return n;
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
        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return limpar(matcher.group(1));
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