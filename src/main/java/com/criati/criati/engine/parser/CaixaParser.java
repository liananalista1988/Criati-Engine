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
                && t.contains("RESUMO DA MOVIMENTAÇÃO")
                && t.contains("SALDO ANTERIOR")
                && t.contains("SALDO BRUTO");

        return t.contains("CAIXA")
                && (layoutExtratoMensal || layoutFundoInvestimento);
    }

    @Override
    public ExtratoInvestimento processar(DocumentoContexto contexto) {

        String texto = contexto.getTexto();

        if (ehLayoutFundoInvestimento(texto)) {
            return processarLayoutFundoInvestimento(contexto);
        }

        return processarLayoutExtratoMensal(contexto);
    }

    private ExtratoInvestimento processarLayoutExtratoMensal(DocumentoContexto contexto) {

        String texto = contexto.getTexto();
        String nomeArquivo = contexto.getNomeArquivo();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("CAIXA");
        extrato.setCompetencia(extrairCompetenciaPorDataFim(texto));
        extrato.setConta(extrairConta(nomeArquivo));

        extrato.setNomeFundo(extrair(texto,
                "(CI CAIXA.*?RL)\\s+CNPJ"));

        extrato.setCnpjFundo(extrair(texto,
                "CI CAIXA.*?CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setAdministrador("CAIXA ECONOMICA FEDERAL");

        extrato.setCnpjAdministrador(extrair(texto,
                "CPF/CNPJ:\\s*(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})\\s+CPF/CNPJ"));

        extrato.setDataInicio(extrair(texto,
                "Data Início\\s+(\\d{2}/\\d{2}/\\d{4})"));

        extrato.setDataFim(extrair(texto,
                "Data Fim\\s+(\\d{2}/\\d{2}/\\d{4})"));

        extrato.setRentabilidadeMes(extrair(texto,
                "Rentabilidade Mês\\s+([0-9,]+%)"));

        extrato.setRentabilidadeAno(extrair(texto,
                "Rentabilidade Ano\\s+([0-9,]+%)"));

        extrato.setRentabilidade12Meses(extrair(texto,
                "Rentabilidade Últimos 12 meses\\s+([0-9,]+%)"));

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

        return extrato;
    }

    private ExtratoInvestimento processarLayoutFundoInvestimento(DocumentoContexto contexto) {

        String texto = contexto.getTexto();

        ExtratoInvestimento extrato = new ExtratoInvestimento();

        extrato.setInstituicao("CAIXA");
        extrato.setAdministrador("CAIXA ECONOMICA FEDERAL");

        extrato.setNomeFundo(limparNomeFundo(extrair(texto,
                "Fundo\\s*\\r?\\n\\s*(.+?)\\s*\\r?\\n\\s*CNPJ do Fundo")));
        
        extrato.setCnpjFundo(extrair(texto,
                "CNPJ do Fundo\\s+(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setCnpjAdministrador(extrair(texto,
                "CNPJ da Administradora\\s+(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"));

        extrato.setConta(limparConta(extrair(texto,
                "Conta Corrente\\s+.*?(\\d{6,}-\\d)")));

        extrato.setCompetencia(extrair(texto,
                "Mês/Ano\\s+(\\d{2}/\\d{4})"));

        extrato.setRentabilidadeMes(extrair(texto,
                "No Mês\\(%\\).*?\\s([0-9]+,[0-9]+)\\s+[0-9]+,[0-9]+\\s+[0-9]+,[0-9]+"));

        extrato.setRentabilidadeAno(extrair(texto,
                "No Mês\\(%\\).*?\\s[0-9]+,[0-9]+\\s+([0-9]+,[0-9]+)\\s+[0-9]+,[0-9]+"));

        extrato.setRentabilidade12Meses(extrair(texto,
                "No Mês\\(%\\).*?\\s[0-9]+,[0-9]+\\s+[0-9]+,[0-9]+\\s+([0-9]+,[0-9]+)"));

        extrato.setSaldoInicial(limparSufixoCredito(extrair(texto,
                "Saldo Anterior\\s+([0-9\\.]+,[0-9]{2}C?)")));

        extrato.setAplicacoes(extrair(texto,
                "Aplicações\\s+([0-9\\.]+,[0-9]{2})"));

        extrato.setResgates(extrair(texto,
                "Resgates\\s+([0-9\\.]+,[0-9]{2})"));

        extrato.setRendimentos(limparSufixoCredito(extrair(texto,
                "Rendimento Bruto no Mês\\s+([0-9\\.]+,[0-9]{2}C?)")));

        extrato.setSaldoFinal(limparSufixoCredito(extrair(texto,
                "Saldo Bruto\\*\\s+([0-9\\.]+,[0-9]{2}C?)")));

        return extrato;
    }

    private boolean ehLayoutFundoInvestimento(String texto) {
        String t = texto.toUpperCase();

        return t.contains("EXTRATO FUNDO DE INVESTIMENTO")
                && t.contains("RESUMO DA MOVIMENTAÇÃO");
    }

    private String extrair(String texto, String regex) {

        Pattern pattern = Pattern.compile(regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private String extrairConta(String nomeArquivo) {

        Pattern pattern = Pattern.compile("(\\d{6,}-\\d)");
        Matcher matcher = pattern.matcher(nomeArquivo);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String extrairCompetenciaPorDataFim(String texto) {

        Pattern pattern = Pattern.compile("Data Fim\\s+\\d{2}/(\\d{2})/(\\d{4})");
        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1) + "/" + matcher.group(2);
        }

        return null;
    }

    private String limparSufixoCredito(String valor) {
        if (valor == null) {
            return null;
        }

        return valor.replace("C", "").trim();
    }
    
    private String limparNomeFundo(String nome) {
        if (nome == null) {
            return null;
        }

        return nome.replace("_", " ").trim();
    }
    
    private String limparConta(String conta) {
        if (conta == null) {
            return null;
        }

        return conta.replaceFirst("^0+", "");
    }
}