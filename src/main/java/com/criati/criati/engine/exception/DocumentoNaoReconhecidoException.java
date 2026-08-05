package com.criati.criati.engine.exception;

/**
 * Lancada quando nenhum parser reconheceu o documento enviado.
 *
 * E diferente de {@link ExtratoIncompletoException}: la o layout foi
 * identificado e o que falhou foi a extracao de um campo; aqui o documento
 * inteiro e desconhecido. O consumidor precisa distinguir os dois para dar
 * a orientacao certa ao usuario ("o layout mudou" x "esse PDF nao e um
 * extrato que sabemos ler").
 */
public class DocumentoNaoReconhecidoException extends RuntimeException {

    public DocumentoNaoReconhecidoException(String message) {
        super(message);
    }
}
