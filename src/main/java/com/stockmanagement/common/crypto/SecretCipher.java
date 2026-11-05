package com.stockmanagement.common.crypto;

/** 비밀값(접근 토큰, API 키) 암호화. 구현은 같은 평문도 매번 다른 암호문을 만든다. */
public interface SecretCipher {

    EncryptedSecret encrypt(String plaintext);

    /** 변조되었거나 다른 키로 암호화된 경우 {@link SecretCipherException}. 메시지에 평문을 담지 않는다. */
    String decrypt(EncryptedSecret secret);
}
