package com.stockmanagement.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SECRET = "sk-ant-test-secret-value-1234";

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY, new SecureRandom());

    @Test
    void roundTrip() {
        EncryptedSecret encrypted = cipher.encrypt(SECRET);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
        assertThat(encrypted.keyVersion()).isEqualTo((short) 1);
    }

    @Test
    void samePlaintextProducesDifferentCiphertextAndTwelveByteIv() {
        EncryptedSecret a = cipher.encrypt(SECRET);
        EncryptedSecret b = cipher.encrypt(SECRET);
        assertThat(a.iv()).hasSize(12);
        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    void ciphertextDoesNotContainPlaintext() {
        EncryptedSecret encrypted = cipher.encrypt(SECRET);
        assertThat(new String(encrypted.ciphertext(), StandardCharsets.ISO_8859_1))
                .doesNotContain("secret");
    }

    @Test
    void tamperedCiphertextFailsWithoutLeakingPlaintext() {
        EncryptedSecret encrypted = cipher.encrypt(SECRET);
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(new EncryptedSecret(tampered, encrypted.iv(), encrypted.keyVersion())))
                .isInstanceOf(SecretCipherException.class)
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void tamperedIvFails() {
        EncryptedSecret encrypted = cipher.encrypt(SECRET);
        byte[] iv = encrypted.iv().clone();
        iv[0] ^= 1;
        assertThatThrownBy(
                        () -> cipher.decrypt(new EncryptedSecret(encrypted.ciphertext(), iv, encrypted.keyVersion())))
                .isInstanceOf(SecretCipherException.class);
    }

    @Test
    void rejectsKeyThatIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmSecretCipher(shortKey, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsBlankKey() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("", new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
