package com.criati.criati.engine.parser;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

public interface DocumentoParser {

    boolean suporta(DocumentoContexto contexto);

    ExtratoInvestimento processar(DocumentoContexto contexto);

    /**
     * Declara quais campos o layout precisa ter extraido para que a posicao
     * possa ser gravada, lancando {@link
     * com.criati.criati.engine.exception.ExtratoIncompletoException} quando
     * faltar algum.
     *
     * Fica separado de {@link #processar} de proposito: /engine/processar e
     * diagnostico e precisa devolver o que conseguiu ler, mesmo incompleto.
     * Quem valida e /engine/extrato-investimento, onde uma posicao pela
     * metade viraria linha errada na planilha do consumidor.
     */
    default void validar(ExtratoInvestimento extrato, DocumentoContexto contexto) {
    }

}
