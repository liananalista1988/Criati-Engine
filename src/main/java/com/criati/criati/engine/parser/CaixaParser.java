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

            ValidadorExtrato.exigir(
                    extrato,
                    "CAIXA / Extrato Mensal",
                    nomeArquivo,
                    "competencia",
                    "conta",
                    "nomeFundo",
                    "cnpjFundo",
                    "saldoFinal",
                    "rentabilidadeMes"
            );
        } else {
            processarFundoInvestimento(texto, extrato);

            ValidadorExtrato.exigir(
                    extrato,
                    "CAIXA / Extrato Fundo de Investimento",
                    nomeArquivo,
                    "competencia",
                    "conta",
                    "nomeFundo",
                    "cnpjFundo",
                    "saldoInicial",
                    "aplicacoes",
                    "resgates",
                    "rendimentos",
                    "saldoFinal",
                    "rentabilidadeMes",
                    "rentabilidadeAno",
                    "rentabilidade12Meses"
            );
        }

        return extrato;
    }

    private void processarExtratoMensal(
            String texto,
            ExtratoInvestimento extrato
    ) {
        extrato.setNomeFundo(limpar(extrair(
                texto,
                "(CI CAIXA.*?RL)\\s+CNPJ"
        )));

        extrato.setCnpjFundo(extrair(
                texto,
                "CI CAIXA.*?CNPJ:\\s*"
                        + "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"
        ));

        extrato.setCnpjAdministrador(extrair(
                texto,
                "CPF/CNPJ:\\s*"
                        + "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"
                        + "\\s+CPF/CNPJ"
        ));

        extrato.setDataInicio(extrair(
                texto,
                "Data Início\\s+(\\d{2}/\\d{2}/\\d{4})"
        ));

        extrato.setDataFim(extrair(
                texto,
                "Data Fim\\s+(\\d{2}/\\d{2}/\\d{4})"
        ));

        extrato.setRentabilidadeMes(extrairPercentualPorRotulo(
                texto,
                "Rentabilidade M[eê]s"
        ));

        extrato.setRentabilidadeAno(extrairPercentualPorRotulo(
                texto,
                "Rentabilidade Ano"
        ));

        extrato.setRentabilidade12Meses(extrairPercentualPorRotulo(
                texto,
                "Rentabilidade Últimos 12 meses"
        ));

        extrato.setSaldoInicial(extrair(
                texto,
                "Saldo Bruto Anterior\\s+R\\$\\s*"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setAplicacoes(extrair(
                texto,
                "Aplicações\\s+R\\$\\s*"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setResgates(extrair(
                texto,
                "Resgates\\s+R\\$\\s*"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setRendimentos(extrair(
                texto,
                "Rendimento Bruto\\s+R\\$\\s*"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setSaldoFinal(extrair(
                texto,
                "Saldo Bruto Final\\s+R\\$\\s*"
                        + "([0-9\\.]+,[0-9]{2})"
        ));
    }

    private void processarFundoInvestimento(
            String texto,
            ExtratoInvestimento extrato
    ) {
        extrato.setNomeFundo(extrairNomeFundoCaixa(texto));

        extrato.setCnpjFundo(extrair(
                texto,
                "CNPJ do Fundo\\s+"
                        + "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"
        ));

        extrato.setCnpjAdministrador(extrair(
                texto,
                "CNPJ da Administradora\\s+"
                        + "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"
        ));

        String[] rentabilidades =
                extrairRentabilidadesFundoCaixa(texto);

        extrato.setRentabilidadeMes(rentabilidades[0]);
        extrato.setRentabilidadeAno(rentabilidades[1]);
        extrato.setRentabilidade12Meses(rentabilidades[2]);

        extrato.setSaldoInicial(extrair(
                texto,
                "Saldo Anterior\\s+"
                        + "([0-9\\.]+,[0-9]{2})\\s*C?"
        ));

        extrato.setAplicacoes(extrair(
                texto,
                "Aplicações\\s+"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setResgates(extrair(
                texto,
                "Resgates\\s+"
                        + "([0-9\\.]+,[0-9]{2})"
        ));

        extrato.setRendimentos(extrair(
                texto,
                "Rendimento Bruto no M[eê]s\\s+"
                        + "([0-9\\.]+,[0-9]{2})\\s*[CD]?"
        ));

        extrato.setSaldoFinal(extrair(
                texto,
                "Saldo Bruto\\*?\\s+"
                        + "([0-9\\.]+,[0-9]{2})\\s*C?"
        ));
    }

    /**
     * Extrai as três rentabilidades do layout:
     *
     * No Mês(%) | No Ano(%) | Nos Últimos 12 Meses(%)
     *
     * A Caixa pode apresentar o sinal negativo depois do número:
     * 0,1218-
     *
     * Dois cuidados obrigatórios neste layout:
     *
     * 1) O cabeçalho quebra de linha no meio ("Nos Últimos 12\nMeses(%)"),
     *    então todo separador do rótulo precisa ser \s+ e não espaço literal.
     *
     * 2) Entre o cabeçalho e os números existe o bloco "Cota em: DD/MM/AAAA".
     *    Se o percentual aceitar número inteiro, o ano da última data casa
     *    primeiro e a rentabilidade do mês sai como "2026" — errado e pior
     *    que null, porque passa despercebido. Por isso o percentual aqui
     *    exige a parte decimal, que a Caixa sempre imprime (ex.: 1,3228).
     */
    private String[] extrairRentabilidadesFundoCaixa(String texto) {
        String percentual =
                "([+\\-−]?[0-9]+,[0-9]+%?[\\-−]?)";

        String regex =
                "No\\s+M[eê]s\\s*\\(%\\)\\s+"
                        + "No\\s+Ano\\s*\\(%\\)\\s+"
                        + "Nos\\s+[ÚU]ltimos\\s+12\\s+Meses\\s*\\(%\\)"
                        + ".*?"
                        + percentual + "\\s+"
                        + percentual + "\\s+"
                        + percentual;

        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (!matcher.find()) {
            return new String[]{null, null, null};
        }

        return new String[]{
                normalizarSinalPercentual(matcher.group(1)),
                normalizarSinalPercentual(matcher.group(2)),
                normalizarSinalPercentual(matcher.group(3))
        };
    }

    /**
     * Extrai percentuais do layout "Extrato Mensal".
     * Também aceita o sinal negativo antes ou depois do número.
     */
    private String extrairPercentualPorRotulo(
            String texto,
            String rotulo
    ) {
        String regex =
                rotulo
                        + "\\s+"
                        + "([+\\-−]?[0-9]+(?:,[0-9]+)?%?[\\-−]?)";

        String percentual = extrair(texto, regex);

        return normalizarSinalPercentual(percentual);
    }

    /**
     * Converte:
     *
     * 0,1218-  -> -0,1218
     * -0,1218  -> -0,1218
     * 0,1218   -> 0,1218
     */
    private String normalizarSinalPercentual(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String percentual = valor
                .replace("\u2212", "-")
                .replace("%", "")
                .replaceAll("\\s+", "")
                .trim();

        boolean negativo =
                percentual.startsWith("-")
                        || percentual.endsWith("-");

        percentual = percentual
                .replace("-", "")
                .replace("+", "");

        if (percentual.isBlank()) {
            return null;
        }

        return negativo
                ? "-" + percentual
                : percentual;
    }

    private String extrairNomeFundoCaixa(String texto) {
        String nome = extrair(
                texto,
                "Fundo\\s+"
                        + "(CAIXA\\s+.*?)"
                        + "(?:\\s+CNPJ do Fundo)"
        );

        if (nome != null) {
            return limpar(nome);
        }

        nome = extrair(
                texto,
                "Fundo\\s+\\n?\\s*"
                        + "(CAIXA\\s+FI\\s+BRASIL\\s+.*?)"
                        + "(?:\\s+CNPJ do Fundo)"
        );

        if (nome != null) {
            return limpar(nome);
        }

        nome = extrair(
                texto,
                "(CAIXA\\s+(?:FIC|FI)\\s+BRASIL\\s+.*?)"
                        + "(?:"
                        + "\\s+CNPJ do Fundo"
                        + "|\\s+\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}"
                        + ")"
        );

        if (nome != null) {
            return limpar(nome);
        }

        nome = extrairNomeFundoPorCnpj(texto);

        if (nome != null) {
            return limpar(nome);
        }

        return null;
    }

    private String extrairNomeFundoPorCnpj(String texto) {
        String cnpj = extrair(
                texto,
                "CNPJ do Fundo\\s+"
                        + "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})"
        );

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

    /**
     * Lê a conta do bloco:
     *
     * Conta Corrente
     * 3703.000575271423-
     * 7
     *
     * O PDF quebra a linha entre o hífen e o dígito verificador, então o
     * padrão precisa aceitar espaço/quebra em volta do hífen. Sem isso a
     * conta voltava null — e a conta compõe a chave da posição
     * (competência + conta + CNPJ), o que gerava linha duplicada a cada
     * reenvio do mesmo extrato.
     *
     * O prefixo de 4 dígitos (agência/operação) continua fora do resultado,
     * como antes, para não mudar a identidade das posições já gravadas.
     */
    private String extrairConta(
            String texto,
            String nomeArquivo
    ) {
        String conta = extrairContaComDigito(
                texto,
                "Conta Corrente\\s+\\d{4}\\s*\\.\\s*(\\d{6,})\\s*-\\s*(\\d)"
        );

        if (conta != null) {
            return removerZerosConta(conta);
        }

        conta = extrairContaComDigito(
                nomeArquivo,
                "(\\d{6,})\\s*-\\s*(\\d)"
        );

        if (conta != null) {
            return removerZerosConta(conta);
        }

        return null;
    }

    private String extrairContaComDigito(
            String texto,
            String regex
    ) {
        if (texto == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1) + "-" + matcher.group(2);
    }

    private String removerZerosConta(String conta) {
        if (conta == null) {
            return null;
        }

        return conta.replaceFirst("^0+", "");
    }

    private String extrairCompetencia(String texto) {
        String competencia = extrair(
                texto,
                "M[eê]s/Ano\\s+(\\d{2}/\\d{4})"
        );

        if (competencia != null) {
            return competencia;
        }

        Pattern pattern = Pattern.compile(
                "Data Fim\\s+\\d{2}/(\\d{2})/(\\d{4})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            return matcher.group(1)
                    + "/"
                    + matcher.group(2);
        }

        return null;
    }

    private String extrair(
            String texto,
            String regex
    ) {
        if (texto == null || regex == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            for (
                    int grupo = 1;
                    grupo <= matcher.groupCount();
                    grupo++
            ) {
                String valor = matcher.group(grupo);

                if (valor != null && !valor.isBlank()) {
                    return limpar(valor);
                }
            }

            return limpar(matcher.group());
        }

        return null;
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }

        return valor
                .replace("\u00A0", " ")
                .replace("\r", "\n");
    }

    private String limpar(String valor) {
        if (valor == null) {
            return null;
        }

        return valor
                .replace("\u00A0", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}