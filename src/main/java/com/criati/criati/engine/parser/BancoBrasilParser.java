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

    /**
     * Rotulo que abre a secao consolidada do fundo. Vem em caixa mista nos
     * DOIS layouts do BB — no anterior so os itens de dentro da secao eram
     * em caixa alta —, e com UNICODE_CASE o "e" acentuado tolera variacao.
     */
    private static final Pattern INICIO_RESUMO_MES = Pattern.compile(
            "Resumo\\s+do\\s+m[eê]s",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Secoes que podem vir logo depois do resumo. A primeira delas fecha a
     * secao consolidada.
     */
    private static final Pattern FIM_RESUMO_MES = Pattern.compile(
            "Valor\\s+da\\s+Cota|Rentabilidade",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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

    /**
     * O documento do BB traz varios fundos, mas "Conta" e "Mes/ano
     * referencia" aparecem uma unica vez, no cabecalho, e sao copiadas para
     * todos os itens. Entao uma falha na leitura da conta nao estraga um
     * item: estraga o array inteiro. Por isso a validacao roda item a item
     * e qualquer falha reprova a requisicao toda.
     *
     * rentabilidadeAno e rentabilidade12Meses ficam de fora da exigencia:
     * fundo aplicado ha poucos meses legitimamente nao tem esses numeros no
     * extrato, e reprovar por isso transformaria importacao boa em erro.
     */
    @Override
    public void validar(ExtratoInvestimento extrato, DocumentoContexto contexto) {
        String fundo = extrato.getNomeFundo() == null
                ? "fundo sem nome identificado"
                : extrato.getNomeFundo();

        ValidadorExtrato.exigir(
                extrato,
                "BB / Consultas - Investimentos Fundos - Mensal (" + fundo + ")",
                contexto.getNomeArquivo(),
                "competencia",
                "conta",
                "nomeFundo",
                "cnpjFundo",
                "saldoInicial",
                "aplicacoes",
                "resgates",
                "rendimentos",
                "saldoFinal",
                "rentabilidadeMes"
        );
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

            // Os cinco consolidados saem da secao "Resumo do mes", e nao do
            // bloco inteiro: a tabela de movimentacoes usa o mesmo
            // vocabulario e vem ANTES do resumo. Ver resumoDoMes().
            String resumo = resumoDoMes(bloco);

            extrato.setSaldoInicial(extrair(resumo,
                    "SALDO\\s+ANTERIOR\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setAplicacoes(extrair(resumo,
                    "APLICAÇÕES\\s*\\(\\+\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setResgates(extrair(resumo,
                    "RESGATES\\s*\\(-\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setRendimentos(extrair(resumo,
                    "RENDIMENTO\\s+BRUTO\\s*\\([\\+\\-]\\)\\s+([\\-]?[0-9\\.]+,[0-9]{2})"));

            extrato.setSaldoFinal(extrair(resumo,
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
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
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

    /**
     * Recorta a secao "Resumo do mes" de dentro do bloco de UM fundo.
     *
     * O bloco carrega duas naturezas de dado que usam o mesmo vocabulario: a
     * tabela de movimentacoes, lancamento a lancamento, e o resumo,
     * consolidado. Sem este recorte, cada campo dependia de algum detalhe do
     * proprio rotulo — "(+)", "(-)", "=", "BRUTO" — para nao casar antes na
     * movimentacao. "Saldo anterior" nao tem detalhe nenhum: e escrito igual
     * nos dois lugares, entao casava sempre na linha errada, e como vinha
     * preenchido a validacao aprovava.
     *
     * O bloco recebido ja termina onde o proximo fundo comeca, entao o
     * recorte nunca alcanca outro fundo. Se o fundo nao tiver a secao,
     * devolve null e os cinco consolidados ficam null: reprovar e melhor do
     * que preencher com numero de outra procedencia.
     */
    private String resumoDoMes(String bloco) {
        Matcher inicio = INICIO_RESUMO_MES.matcher(bloco);

        if (!inicio.find()) {
            return null;
        }

        Matcher fim = FIM_RESUMO_MES.matcher(bloco);

        int corte = fim.find(inicio.end()) ? fim.start() : bloco.length();

        return bloco.substring(inicio.end(), corte);
    }

    private String extrairCompetencia(String texto) {
        Pattern pattern = Pattern.compile(
                "M[eê]s/ano\\s+referência\\s+([A-Za-zçÇ]+)/?(20\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL
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
        // resumoDoMes() devolve null quando o fundo nao tem a secao
        // consolidada; nesse caso o campo simplesmente nao existe.
        if (texto == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL | Pattern.MULTILINE
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