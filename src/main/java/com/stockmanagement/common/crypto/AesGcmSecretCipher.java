package com.stockmanagement.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM (research R5). 마스터 키는 Base64 32바이트, 행마다 무작위 12바이트 IV. */
public final class AesGcmSecretCipher implements SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final short KEY_VERSION = 1;

    private final SecretKeySpec key;
    private final SecureRandom random;

    public AesGcmSecretCipher(String base64Key, SecureRandom random) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("APP_CRYPTO_KEY가 설정되지 않았습니다 (Base64 32바이트 필요)");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("APP_CRYPTO_KEY는 Base64 디코딩 후 32바이트여야 합니다");
        }
        this.key = new SecretKeySpec(raw, "AES");
        this.random = random;
    }

    @Override
    public EncryptedSecret encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(ciphertext, iv, KEY_VERSION);
        } catch (GeneralSecurityException e) {
            throw new SecretCipherException("암호화에 실패했습니다", e);
        }
    }

    @Override
    public String decrypt(EncryptedSecret secret) {
        if (secret.keyVersion() != KEY_VERSION) {
            throw new SecretCipherException("지원하지 않는 키 버전입니다: " + secret.keyVersion(), null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, secret.iv()));
            return new String(cipher.doFinal(secret.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecretCipherException("복호화에 실패했습니다 (변조 또는 키 불일치)", e);
        }
    }
}
