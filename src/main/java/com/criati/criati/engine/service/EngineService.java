package com.criati.criati.engine.service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.DocumentoProcessadoResponse;
import com.criati.criati.engine.parser.BancoBrasilParser;
import com.criati.criati.engine.parser.DocumentoParser;

@Service
public class EngineService {

    private final PdfService pdfService;
    private final List<DocumentoParser> parsers;

    public EngineService(PdfService pdfService, List<DocumentoParser> parsers) {
        this.pdfService = pdfService;
        this.parsers = parsers;
    }

    public DocumentoProcessadoResponse processar(MultipartFile arquivo) throws IOException {
        var pdfTexto = pdfService.extrairTexto(arquivo);

        String texto = pdfTexto.getTexto();

        DocumentoContexto contexto = new DocumentoContexto(
                pdfTexto.getNomeArquivo(),
                texto
        );

        String tipoDocumento = detectarTipoDocumento(texto);
        String instituicao = detectarInstituicao(texto);
        String competencia = extrairCompetencia(texto);

        Object dados = null;

        for (DocumentoParser parser : parsers) {
            if (parser.suporta(contexto)) {

                if (parser instanceof BancoBrasilParser bancoBrasilParser) {
                    dados = bancoBrasilParser.processarTodos(contexto);
                } else {
                    dados = parser.processar(contexto);
                }

                break;
            }
        }

        return new DocumentoProcessadoResponse(
                pdfTexto.getNomeArquivo(),
                tipoDocumento,
                instituicao,
                competencia,
                pdfTexto.getQuantidadeCaracteres(),
                texto,
                dados
        );
    }

    private String detectarTipoDocumento(String texto) {
        String t = texto.toUpperCase();

        if (t.contains("CONSULTAS - INVESTIMENTOS FUNDOS - MENSAL")
                && t.contains("BANCO DO BRASIL")) {
            return "EXTRATO_FUNDO_BB";
        }

        if (t.contains("CONSULTAS - INVESTIMENTOS FUNDOS - MENSAL")
                && t.contains("RESUMO DO MÊS")) {
            return "EXTRATO_FUNDO_BB";
        }

        if (t.contains("EXTRATO CONSOLIDADO")
                && (t.contains("BNB") || t.contains("BANCO DO NORDESTE"))) {
            return "EXTRATO_FUNDO_BNB";
        }

        if (t.contains("EXTRATO MENSAL")
                && t.contains("SALDO BRUTO ANTERIOR")
                && t.contains("SALDO BRUTO FINAL")) {
            return "EXTRATO_FUNDO_CAIXA";
        }

        if (t.contains("EXTRATO FUNDO DE INVESTIMENTO")
                && t.contains("RESUMO DA MOVIMENTAÇÃO")) {
            return "EXTRATO_FUNDO_CAIXA";
        }

        if (t.contains("RELATÓRIO MENSAL CONSOLIDADO")
                || t.contains("RELATORIO MENSAL CONSOLIDADO")
                || t.contains("INVESTIDOR.B3.COM.BR")) {
            return "RELATORIO_B3";
        }

        return "DESCONHECIDO";
    }

    private String detectarInstituicao(String texto) {
        String t = texto.toUpperCase();

        if (t.contains("CONSULTAS - INVESTIMENTOS FUNDOS - MENSAL")) {
            return "BB";
        }

        if (t.contains("EXTRATO CONSOLIDADO")
                && (t.contains("BNB") || t.contains("BANCO DO NORDESTE"))) {
            return "BNB";
        }

        if (t.contains("CAIXA ECONOMICA FEDERAL")
                || t.contains("CAIXA ECONÔMICA FEDERAL")
                || t.contains("EXTRATO FUNDO DE INVESTIMENTO")) {
            return "CAIXA";
        }

        if (t.contains("INVESTIDOR.B3.COM.BR")
                || t.contains("B3")) {
            return "B3";
        }

        return "DESCONHECIDA";
    }

    private String extrairCompetencia(String texto) {
        Pattern referenciaBnb = Pattern.compile(
                "REFERENCIA:\\s*([A-ZÇ]+)/?(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherBnb = referenciaBnb.matcher(texto);

        if (matcherBnb.find()) {
            return mesPorExtenso(matcherBnb.group(1)) + "/" + matcherBnb.group(2);
        }

        Pattern mesAnoTexto = Pattern.compile(
                "M[eê]s/ano\\s+referência\\s+([A-Za-zçÇ]+)/?(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherMesAnoTexto = mesAnoTexto.matcher(texto);

        if (matcherMesAnoTexto.find()) {
            return mesPorExtenso(matcherMesAnoTexto.group(1)) + "/" + matcherMesAnoTexto.group(2);
        }

        Pattern mesAnoNumerico = Pattern.compile(
                "M[eê]s/Ano\\s+(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherMesAnoNumerico = mesAnoNumerico.matcher(texto);

        if (matcherMesAnoNumerico.find()) {
            return matcherMesAnoNumerico.group(1) + "/" + matcherMesAnoNumerico.group(2);
        }

        Pattern dataFimPattern = Pattern.compile(
                "Data Fim\\s+\\d{2}/(\\d{2})/(\\d{4})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher dataFimMatcher = dataFimPattern.matcher(texto);

        if (dataFimMatcher.find()) {
            return dataFimMatcher.group(1) + "/" + dataFimMatcher.group(2);
        }

        Pattern dataB3Pattern = Pattern.compile(
                "Data:\\s*(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher dataB3Matcher = dataB3Pattern.matcher(texto);

        if (dataB3Matcher.find()) {
            return dataB3Matcher.group(1) + "/" + dataB3Matcher.group(2);
        }

        Pattern qualquerCompetenciaPattern = Pattern.compile(
                "(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher qualquerMatcher = qualquerCompetenciaPattern.matcher(texto);

        if (qualquerMatcher.find()) {
            return qualquerMatcher.group();
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
}