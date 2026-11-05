package com.stockmanagement.aicredential.application;

import com.stockmanagement.common.crypto.EncryptedSecret;
import java.util.Objects;

/** 암호화된 API 키와 화면 표시용 끝 4자리. */
public record EncryptedApiKey(EncryptedSecret secret, String last4) {

    public EncryptedApiKey {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(last4, "last4");
    }
}
