package com.criati.criati.engine.service;

import java.io.IOException;
import java.util.List;
import com.criati.criati.engine.model.DocumentoContexto;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.model.DocumentoProcessadoResponse;
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
        
        var contexto = new DocumentoContexto(pdfTexto.getNomeArquivo(), texto);
        String tipoDocumento = detectarTipoDocumento(texto);
        String instituicao = detectarInstituicao(texto);
        String competencia = extrairCompetencia(texto);

        Object dados = null;

        for (DocumentoParser parser : parsers) {
            if (parser.suporta(contexto)) {
                dados = parser.processar(contexto);
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

        if (t.contains("EXTRATO MENSAL")
                && t.contains("SALDO BRUTO ANTERIOR")
                && t.contains("SALDO BRUTO FINAL")) {
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

        if (t.contains("CAIXA ECONOMICA FEDERAL")
                || t.contains("CAIXA ECONÔMICA FEDERAL")) {
            return "CAIXA";
        }

        if (t.contains("INVESTIDOR.B3.COM.BR")
                || t.contains("B3")) {
            return "B3";
        }

        return "DESCONHECIDA";
    }

    private String extrairCompetencia(String texto) {

        Pattern dataFimPattern =
                Pattern.compile("Data Fim\\s+(\\d{2})/(\\d{2})/(\\d{4})");

        Matcher dataFimMatcher = dataFimPattern.matcher(texto);

        if (dataFimMatcher.find()) {
            return dataFimMatcher.group(2) + "/" + dataFimMatcher.group(3);
        }

        Pattern dataB3Pattern =
                Pattern.compile("Data:\\s*(0[1-9]|1[0-2])/(20\\d{2})");

        Matcher dataB3Matcher = dataB3Pattern.matcher(texto);

        if (dataB3Matcher.find()) {
            return dataB3Matcher.group(1) + "/" + dataB3Matcher.group(2);
        }

        Pattern qualquerCompetenciaPattern =
                Pattern.compile("(0[1-9]|1[0-2])/(20\\d{2})");

        Matcher qualquerMatcher = qualquerCompetenciaPattern.matcher(texto);

        if (qualquerMatcher.find()) {
            return qualquerMatcher.group();
        }

        return null;
    }

}