package com.criati.criati.engine.service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.criati.criati.engine.exception.DocumentoNaoReconhecidoException;
import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.DocumentoProcessadoResponse;
import com.criati.criati.engine.model.ExtratoInvestimento;
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

    /**
     * Uso de diagnostico: devolve o que conseguiu ler, mesmo incompleto.
     */
    public DocumentoProcessadoResponse processar(MultipartFile arquivo) throws IOException {
        return processar(arquivo, false);
    }

    /**
     * @param validar quando true, um documento nao reconhecido ou uma posicao
     *                com campo obrigatorio faltando vira excecao em vez de
     *                resposta com null. Usado por /engine/extrato-investimento,
     *                onde o consumidor gravaria a posicao incompleta.
     */
    public DocumentoProcessadoResponse processar(MultipartFile arquivo, boolean validar) throws IOException {
        var pdfTexto = pdfService.extrairTexto(arquivo);

        String texto = pdfTexto.getTexto();

        DocumentoContexto contexto = new DocumentoContexto(
                pdfTexto.getNomeArquivo(),
                texto
        );

        String tipoDocumento = detectarTipoDocumento(texto);
        String instituicao = detectarInstituicao(texto);
        String competencia = extrairCompetencia(texto);

        DocumentoParser parserEscolhido = null;
        Object dados = null;

        for (DocumentoParser parser : parsers) {
            if (parser.suporta(contexto)) {
                parserEscolhido = parser;

                if (parser instanceof BancoBrasilParser bancoBrasilParser) {
                    dados = bancoBrasilParser.processarTodos(contexto);
                } else {
                    dados = parser.processar(contexto);
                }

                break;
            }
        }

        if (validar) {
            validarDados(dados, parserEscolhido, contexto, tipoDocumento, instituicao);
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

    /**
     * Um PDF com varios fundos e reprovado por inteiro se qualquer item
     * estiver incompleto — nao existe resposta parcial. No layout do BB a
     * conta e a competencia vem do cabecalho e sao copiadas para todos os
     * itens, entao "um item ruim" quase sempre significa que o cabecalho
     * falhou e o array inteiro esta comprometido.
     */
    private void validarDados(
            Object dados,
            DocumentoParser parser,
            DocumentoContexto contexto,
            String tipoDocumento,
            String instituicao
    ) {
        if (dados == null) {
            throw new DocumentoNaoReconhecidoException(
                    "Nenhum parser reconheceu o documento \"" + contexto.getNomeArquivo() + "\""
                            + " (tipo detectado: " + tipoDocumento
                            + ", instituicao detectada: " + instituicao + ")."
                            + " Nenhuma posicao foi retornada.");
        }

        if (dados instanceof List<?> lista) {
            if (lista.isEmpty()) {
                throw new ExtratoIncompletoException(
                        "O documento \"" + contexto.getNomeArquivo() + "\" foi reconhecido"
                                + " (tipo " + tipoDocumento + "), mas nenhum fundo foi"
                                + " encontrado dentro dele. Nenhuma posicao foi retornada.",
                        List.of());
            }

            for (Object item : lista) {
                parser.validar((ExtratoInvestimento) item, contexto);
            }

            return;
        }

        parser.validar((ExtratoInvestimento) dados, contexto);
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

        if (t.contains("BANCO DO NORDESTE")
                && (t.contains("SISTEMA FUNDOS DE INVESTIMENTO") || t.contains("SANTANDER SECURITIES SERVICES"))) {
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

        if (t.contains("BANCO DO NORDESTE")
                && (t.contains("SISTEMA FUNDOS DE INVESTIMENTO") || t.contains("SANTANDER SECURITIES SERVICES"))) {
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

        // Layout BNB "Extrato Mensal": a data de emissao (DD/MM/AAAA) vem
        // seguida, na mesma linha, do mes/ano de competencia (MM/AAAA).
        // Precisa vir antes do fallback generico abaixo, senao o fallback
        // acaba capturando o mes/ano de dentro da propria data de emissao.
        Pattern competenciaAoLadoDataEmissao = Pattern.compile(
                "\\d{2}/\\d{2}/20\\d{2}\\s+(0[1-9]|1[0-2])/(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcherCompetenciaAoLado = competenciaAoLadoDataEmissao.matcher(texto);

        if (matcherCompetenciaAoLado.find()) {
            return matcherCompetenciaAoLado.group(1) + "/" + matcherCompetenciaAoLado.group(2);
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