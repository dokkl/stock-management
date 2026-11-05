package com.stockmanagement.market.adapter.toss;

import com.stockmanagement.common.crypto.EncryptedSecret;
import com.stockmanagement.common.crypto.SecretCipher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 토스증권 접근 토큰을 암호화해 DB에 캐시한다. 클라이언트당 유효 토큰이 1개라서(재발급 시 이전 토큰 즉시 무효)
 * 불필요한 재발급을 피하고, 만료 5분 전에만 다시 발급한다.
 */
@Component
public class TossTokenStore {

    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final String PROVIDER = "TOSS";

    private final TossHttpClient httpClient;
    private final JdbcClient jdbc;
    private final SecretCipher cipher;
    private final Clock clock;

    public TossTokenStore(TossHttpClient httpClient, JdbcClient jdbc, SecretCipher cipher, Clock clock) {
        this.httpClient = httpClient;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        Instant now = clock.instant();
        return load().filter(token -> now.isBefore(token.expiresAt().minus(REFRESH_MARGIN)))
                .map(token -> cipher.decrypt(token.secret()))
                .orElseGet(() -> issue(now));
    }

    /** 서버가 토큰을 거부(expired-token 등)했을 때 다음 호출에서 재발급되도록 캐시를 버린다. */
    public synchronized void invalidate() {
        jdbc.sql("DELETE FROM market_token WHERE provider = :provider")
                .param("provider", PROVIDER)
                .update();
    }

    private String issue(Instant now) {
        TossHttpClient.TokenResponse response = httpClient.issueToken();
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("토스증권 토큰 응답이 비어 있습니다");
        }
        EncryptedSecret secret = cipher.encrypt(response.accessToken());
        jdbc.sql("""
                        INSERT INTO market_token (provider, ciphertext, iv, key_version, expires_at)
                        VALUES (:provider, :ciphertext, :iv, :keyVersion, :expiresAt)
                        ON CONFLICT (provider) DO UPDATE SET ciphertext = EXCLUDED.ciphertext, iv = EXCLUDED.iv,
                            key_version = EXCLUDED.key_version, expires_at = EXCLUDED.expires_at
                        """)
                .param("provider", PROVIDER)
                .param("ciphertext", secret.ciphertext())
                .param("iv", secret.iv())
                .param("keyVersion", secret.keyVersion())
                .param("expiresAt", Timestamp.from(now.plusSeconds(response.expiresIn())))
                .update();
        return response.accessToken();
    }

    private Optional<StoredToken> load() {
        return jdbc.sql("SELECT ciphertext, iv, key_version, expires_at FROM market_token WHERE provider = :provider")
                .param("provider", PROVIDER)
                .query((rs, n) -> new StoredToken(
                        new EncryptedSecret(rs.getBytes("ciphertext"), rs.getBytes("iv"), rs.getShort("key_version")),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    private record StoredToken(EncryptedSecret secret, Instant expiresAt) {}
}
