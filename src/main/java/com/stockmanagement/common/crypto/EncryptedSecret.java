package com.stockmanagement.common.crypto;

import java.util.Objects;

/** AES-GCM 암호문(인증 태그 포함), 12바이트 IV, 마스터 키 버전. */
public final class EncryptedSecret {

    private final byte[] ciphertext;
    private final byte[] iv;
    private final short keyVersion;

    public EncryptedSecret(byte[] ciphertext, byte[] iv, short keyVersion) {
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        this.iv = Objects.requireNonNull(iv, "iv").clone();
        this.keyVersion = keyVersion;
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    public byte[] iv() {
        return iv.clone();
    }

    public short keyVersion() {
        return keyVersion;
    }

    @Override
    public String toString() {
        return "EncryptedSecret[keyVersion=" + keyVersion + "]";
    }
}
