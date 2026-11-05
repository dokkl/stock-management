package com.stockmanagement.common.config;

import com.stockmanagement.common.crypto.AesGcmSecretCipher;
import com.stockmanagement.common.crypto.SecretCipher;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CryptoConfig {

    @Bean
    SecretCipher secretCipher(@Value("${app.crypto.key}") String base64Key) {
        return new AesGcmSecretCipher(base64Key, new SecureRandom());
    }
}
