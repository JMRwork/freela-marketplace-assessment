package br.com.freela.contrato.application;

public class ContratoNaoEncontradoException extends RuntimeException {
    public ContratoNaoEncontradoException(String message) {
        super(message);
    }
}
