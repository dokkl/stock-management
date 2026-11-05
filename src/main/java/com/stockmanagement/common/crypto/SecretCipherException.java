package com.stockmanagement.common.crypto;

/** 암·복호화 실패. 원인 메시지에도 평문이나 키를 담지 않는다. */
public class SecretCipherException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SecretCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
