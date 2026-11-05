package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.common.crypto.EncryptedSecret;
import com.stockmanagement.common.crypto.SecretCipher;
import org.springframework.stereotype.Component;

/** ApiKey ↔ 암호문 변환(research R5). 암호화 자체는 공통 SecretCipher(AES-256-GCM)가 한다. */
@Component
public class CredentialCipher {

    private final SecretCipher cipher;

    public CredentialCipher(SecretCipher cipher) {
        this.cipher = cipher;
    }

    public EncryptedApiKey encrypt(ApiKey key) {
        return new EncryptedApiKey(cipher.encrypt(key.reveal()), key.last4());
    }

    public ApiKey decrypt(EncryptedSecret secret) {
        return ApiKey.of(cipher.decrypt(secret));
    }
}
