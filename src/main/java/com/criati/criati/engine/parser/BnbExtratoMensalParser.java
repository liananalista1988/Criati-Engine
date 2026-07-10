package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Layout "Sistema Fundos de Investimento / Extrato Mensal" do Banco do
 * Nordeste (BNB), administrado pela Santander Securities Services Brasil
 * DTVM. Este layout e diferente do "EXTRATO CONSOLIDADO" ja tratado por
 * {@link BnbParser}: os rotulos de saldo sao "SALDO ANTERIOR" / "SALDO
 * FINAL" (em vez de "SALDO INICIAL" / "SALDO FINAL"), a competencia
 * aparece como "Mes/Ano" ao lado da data de emissao, e o resumo de
 * movimentacao usa os rotulos "Total Aplicacoes" / "Total Resgates".
 */
@Component
public class BnbExtratoMensalParser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = normalizar(contexto.getTexto()).toUpperCase();

        boolean ehBnb = t.contains("BANCO DO NORDESTE") || t.contains("BNB SOBERANO") || t.contains("MOVIMENTACOES BNB");

        boolean ehLayoutExtratoMensal = t.contains("SISTEMA FUNDOS DE INVESTIMENTO")
                || (t.contains("EXTRATO MENSAL") && t.contains("SANTANDER SECURITIES SERVICES"));

        // Nao conflitar com o layout "EXTRATO CONSOLIDADO" (BnbParser) nem
        // com o layout da Caixa, que tambem usa "EXTRATO MENSAL".
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

        // Linha "Rend. Mensal / Rend. Anual / Ult. 12 Meses" (podem nao
        // vir preenchidos em todos os extratos - nesse caso calculamos a
        // rentabilidade do mes a partir do valor da cota, mais abaixo).
        extrato.setRentabilidadeMes(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidadeAno(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9]+,[0-9]+"));
        extrato.setRentabilidade12Meses(extrair(texto,
                "BNB\\s+SOBERANO\\s+FIF\\s+[\\-]?[0-9]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)"));

        // "SALDO ANTERIOR  <cotas>  <valor da cota>  <valor em R$>"
        extrato.setSaldoInicial(extrair(texto,
                "SALDO\\s+ANTERIOR\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        // ".SALDO FINAL ..." - o "." antes de SALDO e um artefato comum de
        // extracao (marcador de linha), por isso e opcional.
        extrato.setSaldoFinal(extrair(texto,
                "\\.?\\s*SALDO\\s+FINAL\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+[\\-]?[0-9]+,[0-9]+\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        // Quando o extrato nao traz o percentual de rentabilidade do mes
        // pronto (comum nesse layout), calculamos a partir do valor da
        // cota anterior e do valor da cota final:
        //   rentabilidadeMes = (cotaFinal / cotaInicial - 1) * 100
        if (extrato.getRentabilidadeMes() == null) {
            String rentabilidadeCalculada = calcularRentabilidadeMesPorCota(texto);

            if (rentabilidadeCalculada != null) {
                extrato.setRentabilidadeMes(rentabilidadeCalculada);
            }
        }

        String cabecalhoResumo = "TOTAL\\s+APLICA[CÇ][OÕ]ES\\s+TOTAL\\s+RESGATES\\s+REND\\.?\\s*BRUTO\\s+MENSAL\\s+I\\.?R\\.?\\s+FEDERAL";

        extrato.setAplicacoes(extrair(texto,
                cabecalhoResumo + "\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setResgates(extrair(texto,
                cabecalhoResumo + "\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));
        extrato.setRendimentos(extrair(texto,
                cabecalhoResumo + "\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+[\\-]?[0-9\\.]+,[0-9]{2}\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

        return extrato;
    }

    /**
     * Calcula a rentabilidade do mes com base no valor da cota anterior e
     * no valor da cota final, quando o extrato nao traz esse percentual
     * pronto:
     *   rentabilidadeMes = (valorCotaFinal / valorCotaInicial - 1) * 100
     */
    private String calcularRentabilidadeMesPorCota(String texto) {
        String cotaAnteriorTexto = extrair(texto,
                "SALDO\\s+ANTERIOR\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9\\.]+,[0-9]{2}");

        String cotaFinalTexto = extrair(texto,
                "\\.?\\s*SALDO\\s+FINAL\\s+[\\-]?[0-9\\.]+,[0-9]+\\s+([\\-]?[0-9]+,[0-9]+)\\s+[\\-]?[0-9\\.]+,[0-9]{2}");

        Double cotaAnterior = paraNumero(cotaAnteriorTexto);
        Double cotaFinal = paraNumero(cotaFinalTexto);

        if (cotaAnterior == null || cotaFinal == null || cotaAnterior == 0) {
            return null;
        }

        double rentabilidade = (cotaFinal / cotaAnterior - 1) * 100;

        return formatarNumeroBr(rentabilidade);
    }

    private Double paraNumero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        try {
            String normalizado = valor.trim()
                    .replace(".", "")
                    .replace(",", ".");

            return Double.parseDouble(normalizado);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatarNumeroBr(double valor) {
        String formatado = String.format(java.util.Locale.US, "%.4f", valor);
        return formatado.replace(".", ",");
    }

    private String extrairConta(String texto) {
        // Ex.: "059 - SAO LUIS CENTRO   000131774-9   1"
        String conta = extrair(texto, "\\d{2,3}\\s*-\\s*[A-ZÀ-Ú\\s]+?\\s+(\\d{5,9}-\\d)\\s+\\d+");

        if (conta != null) {
            return conta;
        }

        // Fallback mais generico: qualquer numero de conta no formato
        // "digitos-digito" presente no documento.
        return extrair(texto, "(\\d{6,9}-\\d)");
    }

    private String extrairCompetencia(String texto) {
        // "Nome / Data Emissao / Mes/Ano" -> linha de dados no formato
        // "... 06/07/2026 06/2026" (data de emissao seguida da competencia).
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