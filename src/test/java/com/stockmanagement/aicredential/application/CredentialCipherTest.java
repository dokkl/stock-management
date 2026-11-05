package com.stockmanagement.aicredential.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.common.crypto.AesGcmSecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    private static final String RAW = "sk-proj-very-secret-key-abcd";

    private final CredentialCipher cipher = new CredentialCipher(
            new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[32]), new SecureRandom()));

    @Test
    void encryptsWithLastFourAndRoundTrips() {
        EncryptedApiKey encrypted = cipher.encrypt(ApiKey.of(RAW));

        assertThat(encrypted.last4()).isEqualTo("abcd");
        assertThat(cipher.decrypt(encrypted.secret())).isEqualTo(ApiKey.of(RAW));
    }

    @Test
    void ciphertextDoesNotContainPlaintext() {
        EncryptedApiKey encrypted = cipher.encrypt(ApiKey.of(RAW));

        String bytes = new String(encrypted.secret().ciphertext(), StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain(RAW).doesNotContain("secret");
        assertThat(encrypted.toString()).doesNotContain(RAW);
    }

    @Test
    void sameKeyEncryptsDifferently() {
        assertThat(cipher.encrypt(ApiKey.of(RAW)).secret().iv())
                .isNotEqualTo(cipher.encrypt(ApiKey.of(RAW)).secret().iv());
    }
}
