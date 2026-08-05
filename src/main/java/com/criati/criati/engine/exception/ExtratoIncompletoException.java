package com.criati.criati.engine.exception;

import java.util.List;

/**
 * Lancada quando um parser consegue identificar o documento, mas nao consegue
 * extrair algum campo obrigatorio do layout.
 *
 * Existe para evitar o pior cenario do consumidor: receber 200 OK com campos
 * null e gravar a posicao pela metade. Como a "conta" faz parte da chave de
 * identidade da posicao (competencia + conta + CNPJ), um null silencioso gera
 * linha duplicada a cada reenvio do mesmo extrato.
 */
public class ExtratoIncompletoException extends RuntimeException {

    private final List<String> camposFaltando;

    public ExtratoIncompletoException(String message, List<String> camposFaltando) {
        super(message);
        this.camposFaltando = camposFaltando;
    }

    public List<String> getCamposFaltando() {
        return camposFaltando;
    }
}
