package com.criati.criati.engine.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.criati.criati.engine.exception.ExtratoIncompletoException;
import com.criati.criati.engine.model.ExtratoInvestimento;

/**
 * Verifica se os campos obrigatorios de um layout foram realmente extraidos.
 *
 * A validacao e declarada por layout (e nao global) de proposito: o B3, por
 * exemplo, nao tem rentabilidade no documento e zera esses campos na origem.
 * Exigir os mesmos campos de todos os parsers quebraria fluxos que hoje
 * funcionam.
 */
public final class ValidadorExtrato {

    /**
     * Nomes iguais aos do JSON de resposta, porque e isso que o consumidor
     * (Apps Script) le e e isso que a mensagem de erro precisa citar.
     */
    private static final Map<String, Function<ExtratoInvestimento, String>> ACESSORES =
            criarAcessores();

    private ValidadorExtrato() {
    }

    public static void exigir(
            ExtratoInvestimento extrato,
            String layout,
            String nomeArquivo,
            String... camposObrigatorios
    ) {
        List<String> faltando = new ArrayList<>();

        for (String campo : camposObrigatorios) {
            Function<ExtratoInvestimento, String> acessor = ACESSORES.get(campo);

            if (acessor == null) {
                throw new IllegalArgumentException(
                        "Campo desconhecido na validacao do extrato: " + campo);
            }

            String valor = acessor.apply(extrato);

            if (valor == null || valor.isBlank()) {
                faltando.add(campo);
            }
        }

        if (faltando.isEmpty()) {
            return;
        }

        String mensagem = "Nao foi possivel extrair "
                + (faltando.size() == 1 ? "o campo obrigatorio" : "os campos obrigatorios")
                + " " + String.join(", ", faltando)
                + " do documento \"" + nomeArquivo + "\""
                + " (layout " + layout + ")."
                + " O documento foi reconhecido, mas o layout mudou ou veio incompleto."
                + " A posicao NAO foi retornada para evitar gravacao com dados"
                + " faltando (a conta faz parte da chave da posicao).";

        throw new ExtratoIncompletoException(mensagem, faltando);
    }

    private static Map<String, Function<ExtratoInvestimento, String>> criarAcessores() {
        Map<String, Function<ExtratoInvestimento, String>> acessores = new LinkedHashMap<>();

        acessores.put("competencia", ExtratoInvestimento::getCompetencia);
        acessores.put("instituicao", ExtratoInvestimento::getInstituicao);
        acessores.put("conta", ExtratoInvestimento::getConta);
        acessores.put("nomeFundo", ExtratoInvestimento::getNomeFundo);
        acessores.put("cnpjFundo", ExtratoInvestimento::getCnpjFundo);
        acessores.put("saldoInicial", ExtratoInvestimento::getSaldoInicial);
        acessores.put("aplicacoes", ExtratoInvestimento::getAplicacoes);
        acessores.put("resgates", ExtratoInvestimento::getResgates);
        acessores.put("rendimentos", ExtratoInvestimento::getRendimentos);
        acessores.put("saldoFinal", ExtratoInvestimento::getSaldoFinal);
        acessores.put("rentabilidadeMes", ExtratoInvestimento::getRentabilidadeMes);
        acessores.put("rentabilidadeAno", ExtratoInvestimento::getRentabilidadeAno);
        acessores.put("rentabilidade12Meses", ExtratoInvestimento::getRentabilidade12Meses);

        return acessores;
    }
}
