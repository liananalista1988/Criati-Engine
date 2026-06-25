package com.criati.criati.engine.parser;

import com.criati.criati.engine.model.DocumentoContexto;
import com.criati.criati.engine.model.ExtratoInvestimento;

public interface DocumentoParser {

    boolean suporta(DocumentoContexto contexto);

    ExtratoInvestimento processar(DocumentoContexto contexto);

}