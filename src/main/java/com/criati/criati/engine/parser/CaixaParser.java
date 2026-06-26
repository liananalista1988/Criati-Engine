package com.criati.criati.engine.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class CaixaParser implements DocumentoParser {

    @Override
    public boolean suporta(DocumentoContexto contexto) {
        String t = contexto.getTexto().toUpperCase();

        boolean layoutExtratoMensal =
                t.contains("EXTRATO MENSAL")
                        && t.contains("SALDO BRUTO ANTERIOR")
                        && t.contains("SALDO BRUTO FINAL");

        boolean layoutFundoInvestimento =
                t.contains("EXTRATO FUNDO DE INVESTIMENTO")
                        && t.contains("RESUMO DA MOVIMENTAÇÃO");

        return t.contains("CAIXA")
                && (layoutExtratoMensal || layoutFundoInvestimento);
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {
        String texto = normalizar(contexto.getTexto());
        String nomeArquivo = contexto.getNomeArquivo();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("CAIXA");
        extrato.setAdministrador("CAIXA ECONOMICA FEDERAL");
        extrato.setConta(extrairConta(texto, nomeArquivo));
        extrato.setCompetencia(extrairCompetencia(texto));

        if (ehLayoutExtratoMensal(texto)) {
            processarExtratoMensal(texto, extrato);
        } else {
            processarFundoInvestimento(texto, extrato);
        }

        return extrato;
    }

    private void processarExtratoMensal(String texto, ExtratoInvestimento extrato) {
        extrato.setNomeFundo(limpar(extrair(texto,
                "(CI CAIXA.*?RL)\\s+CNPJ")));

        extrato.setCnpjFundo(extrair(texto,
                "CI CAIXA.*?CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setCnpjAdministrador(extrair(texto,
                "CPF/CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})\\s+CPF/CNPJ"));

        extrato.setDataInicio(extrair(texto,
                "Data Início\\s+(\\d{2}/\\d{2}/\\d{4})"));

        extrato.setDataFim(extrair(texto,
                "Data Fim\\s+(\\d{2}/\\d{2}/\\d{4})"));

        extrato.setRentabilidadeMes(extrair(texto,
                "Rentabilidade Mês\\s+([0-9,]+%?)"));

        extrato.setRentabilidadeAno(extrair(texto,
                "Rentabilidade Ano\\s+([0-9,]+%?)"));

        extrato.setRentabilidade12Meses(extrair(texto,
                "Rentabilidade Últimos 12 meses\\s+([0-9,]+%?)"));

        extrato.setSaldoInicial(extrair(texto,
                "Saldo Bruto Anterior\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})"));

        extrato.setAplicacoes(extrair(texto,
                "Aplicações\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})"));

        extrato.setResgates(extrair(texto,
                "Resgates\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})"));

        extrato.setRendimentos(extrair(texto,
                "Rendimento Bruto\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})"));

        extrato.setSaldoFinal(extrair(texto,
                "Saldo Bruto Final\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})"));
    }

    private void processarFundoInvestimento(String texto, ExtratoInvestimento extrato) {
        extrato.setNomeFundo(extrairNomeFundoCaixa(texto));

        extrato.setCnpjFundo(extrair(texto,
                "CNPJ do Fundo\\s+(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setCnpjAdministrador(extrair(texto,
                "CNPJ da Administradora\\s+(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setRentabilidadeMes(extrair(texto,
                "No M[eê]s\\(%\\)\\s+No Ano\\(%\\)\\s+Nos Últimos 12 Meses\\(%\\).*?([0-9]+,[0-9]+)"));

        extrato.setRentabilidadeAno(extrair(texto,
                "No M[eê]s\\(%\\)\\s+No Ano\\(%\\)\\s+Nos Últimos 12 Meses\\(%\\).*?[0-9]+,[0-9]+\\s+([0-9]+,[0-9]+)"));

        extrato.setRentabilidade12Meses(extrair(texto,
                "No M[eê]s\\(%\\)\\s+No Ano\\(%\\)\\s+Nos Últimos 12 Meses\\(%\\).*?[0-9]+,[0-9]+\\s+[0-9]+,[0-9]+\\s+([0-9]+,[0-9]+)"));

        extrato.setSaldoInicial(extrair(texto,
                "Saldo Anterior\\s+([0-9\\.]+,[0-9]{2})\\s*C?"));

        extrato.setAplicacoes(extrair(texto,
                "Aplicações\\s+([0-9\\.]+,[0-9]{2})"));

        extrato.setResgates(extrair(texto,
                "Resgates\\s+([0-9\\.]+,[0-9]{2})"));

        extrato.setRendimentos(extrair(texto,
                "Rendimento Bruto no M[eê]s\\s+([0-9\\.]+,[0-9]{2})\\s*C?"));

        extrato.setSaldoFinal(extrair(texto,
                "Saldo Bruto\\*?\\s+([0-9\\.]+,[0-9]{2})\\s*C?"));
    }

    private String extrairNomeFundoCaixa(String texto) {
        String nome = extrair(texto,
                "Fundo\\s+(CAIXA\\s+.*?)(?:\\s+CNPJ do Fundo)");

        if (nome != null) return limpar(nome);

        nome = extrair(texto,
                "Fundo\\s+\\n?\\s*(CAIXA\\s+FI\\s+BRASIL\\s+.*?)(?:\\s+CNPJ do Fundo)");

        if (nome != null) return limpar(nome);

        nome = extrair(texto,
                "(CAIXA\\s+(?:FIC|FI)\\s+BRASIL\\s+.*?)(?:\\s+CNPJ do Fundo|\\s+\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})");

        if (nome != null) return limpar(nome);

        nome = extrairNomeFundoPorCnpj(texto);

        if (nome != null) return limpar(nome);

        return null;
    }

    private String extrairNomeFundoPorCnpj(String texto) {
        String cnpj = extrair(texto,
                "CNPJ do Fundo\\s+(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})");

        if ("10.740.670/0001-06".equals(cnpj)) {
            return "CAIXA FI BRASIL IRF-M1 TP RF";
        }

        return null;
    }
    
    private boolean ehLayoutExtratoMensal(String texto) {
        String t = texto.toUpperCase();

        return t.contains("EXTRATO MENSAL")
                && t.contains("SALDO BRUTO ANTERIOR")
                && t.contains("SALDO BRUTO FINAL");
    }

    private String extrairConta(String texto, String nomeArquivo) {
        String conta = extrair(texto,
                "Conta Corrente\\s+\\d{4}\\.(\\d{6,}-\\d)");

        if (conta != null) return removerZerosConta(conta);

        conta = extrair(nomeArquivo, "(\\d{6,}-\\d)");

        if (conta != null) return removerZerosConta(conta);

        return null;
    }

    private String removerZerosConta(String conta) {
        if (conta == null) return null;
        return conta.replaceFirst("^0+", "");
    }

    private String extrairCompetencia(String texto) {
        String competencia = extrair(texto,
                "M[eê]s/Ano\\s+(\\d{2}/\\d{4})");

        if (competencia != null) return competencia;

        Pattern p = Pattern.compile(
                "Data Fim\\s+\\d{2}/(\\d{2})/(\\d{4})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = p.matcher(texto);

        if (m.find()) {
            return m.group(1) + "/" + m.group(2);
        }

        return null;
    }

    private String extrair(String texto, String regex) {
        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

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