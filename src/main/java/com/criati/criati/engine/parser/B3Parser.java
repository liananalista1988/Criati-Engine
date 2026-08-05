package com.criati.criati.engine.parser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

@Component
public class B3Parser implements DocumentoParser {

    /**
     * O relatorio da B3 nao imprime o CNPJ do fundo em lugar nenhum: o unico
     * CNPJ do documento e o do cotista, no cabecalho. Como o CNPJ compoe a
     * chave da posicao no consumidor (competencia + conta + CNPJ), e como a
     * conta da B3 e sempre o literal "B3", sem essa tabela todos os FIIs
     * cairiam sob a mesma chave e um sobrescreveria o outro.
     *
     * Para incluir um FII novo: acrescente o ticker aqui. Enquanto ele nao
     * estiver na tabela, /engine/extrato-investimento reprova o relatorio
     * com 422 apontando cnpjFundo — de proposito, para que um fundo novo
     * apareca como erro visivel e nao como linha sem identidade.
     */
    private static final Map<String, String> CNPJ_POR_TICKER = Map.of(
            "RBRD11", "09.006.914/0001-34"
    );

    private static final Pattern TICKER_FII = Pattern.compile("\\b([A-Z]{4}11)\\b");

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

        // Fica null quando nao ha ticker ou quando ele nao esta na tabela;
        // quem reprova e validar(), para /engine/processar continuar
        // diagnosticando. (Map.of() lanca NPE em get(null), dai a guarda.)
        String ticker = primeiroTicker(texto);

        extrato.setCnpjFundo(ticker == null ? null : CNPJ_POR_TICKER.get(ticker));

        extrato.setAdministrador(
                extrair(texto, "(BB\\s+BANCO\\s+DE\\s+INVESTIMENTO\\s+S/A)")
        );

        String movimentacao = extrairMovimentacao(texto);

        extrato.setSaldoInicial(null);

        // B3/FII:
        // Os proventos recebidos entram como movimentação.
        // O rendimento real deve ser calculado depois:
        // (Saldo Atual - Saldo Anterior) + Movimentação
        extrato.setAplicacoes(movimentacao);
        extrato.setResgates("0,00");
        extrato.setRendimentos(null);

        extrato.setSaldoFinal(extrairSaldoFinal(texto));

        extrato.setRentabilidadeMes(null);
        extrato.setRentabilidadeAno(null);
        extrato.setRentabilidade12Meses(null);

        return extrato;
    }

    /**
     * saldoInicial, rendimentos e as rentabilidades ficam de fora porque o
     * relatorio da B3 nao os traz: sao null na origem, por decisao do
     * layout, e exigi-los reprovaria todo relatorio valido.
     */
    @Override
    public void validar(ExtratoInvestimento extrato, DocumentoContexto contexto) {
        String nomeArquivo = contexto.getNomeArquivo();
        Set<String> tickers = extrairTickers(contexto.getTexto());

        // processar() devolve UMA posicao e extrairNomeFundo() pega o
        // primeiro fundo que encontra. Com dois FIIs no mesmo relatorio o
        // segundo seria descartado em silencio — e, como a conta da B3 e
        // fixa, os dois ainda disputariam a mesma chave na planilha.
        // Reprovar o arquivo inteiro e a unica saida honesta enquanto este
        // parser nao souber devolver varias posicoes.
        if (tickers.size() > 1) {
            throw new ExtratoIncompletoException(
                    "O relatorio \"" + nomeArquivo + "\" traz " + tickers.size()
                            + " fundos (" + String.join(", ", tickers) + "), mas o leitor"
                            + " da B3 so sabe devolver uma posicao por arquivo."
                            + " Nenhuma posicao foi retornada, para nao gravar um fundo"
                            + " e perder o outro. Gere um relatorio por fundo.",
                    List.of());
        }

        ValidadorExtrato.exigir(
                extrato,
                tickers.isEmpty()
                        ? "B3 / Relatorio Mensal Consolidado"
                        : "B3 / Relatorio Mensal Consolidado (ticker "
                                + tickers.iterator().next()
                                + " sem CNPJ cadastrado em B3Parser.CNPJ_POR_TICKER)",
                nomeArquivo,
                "competencia",
                "conta",
                "nomeFundo",
                "cnpjFundo",
                "saldoFinal"
        );
    }

    /**
     * Tickers de FII na B3 sao quatro letras seguidas de "11" (RBRD11).
     * Devolve na ordem em que aparecem, sem repetir.
     */
    private Set<String> extrairTickers(String texto) {
        Set<String> tickers = new LinkedHashSet<>();
        Matcher matcher = TICKER_FII.matcher(texto);

        while (matcher.find()) {
            tickers.add(matcher.group(1));
        }

        return tickers;
    }

    private String primeiroTicker(String texto) {
        Set<String> tickers = extrairTickers(texto);

        return tickers.isEmpty() ? null : tickers.iterator().next();
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

    private String extrairMovimentacao(String texto) {
        Pattern p = Pattern.compile(
                "Proventos\\s+recebidos.*?Total\\s+R\\$\\s*([0-9\\.]+,[0-9]{2})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = p.matcher(texto);

        if (m.find()) {
            return m.group(1).trim();
        }

        return "0,00";
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