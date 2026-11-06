package com.stockmanagement.aicredential.persistence;

import com.stockmanagement.aicredential.application.EncryptedApiKey;
import com.stockmanagement.aicredential.application.StoredCredential;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.crypto.EncryptedSecret;
import com.stockmanagement.user.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCredentialStore implements CredentialStore {

    private final JdbcClient jdbc;

    JdbcCredentialStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StoredCredential> findAll(UserId userId) {
        return jdbc.sql("SELECT provider, last4, verified_at FROM ai_credential WHERE user_id = :userId ORDER BY id")
                .param("userId", userId.value())
                .query((rs, n) -> new StoredCredential(
                        AiProvider.valueOf(rs.getString("provider")),
                        rs.getString("last4"),
                        rs.getTimestamp("verified_at").toInstant()))
                .list();
    }

    @Override
    public Optional<EncryptedSecret> findSecret(UserId userId, AiProvider provider) {
        return jdbc.sql("""
                        SELECT ciphertext, iv, key_version FROM ai_credential
                        WHERE user_id = :userId AND provider = :provider
                        """)
                .param("userId", userId.value())
                .param("provider", provider.name())
                .query((rs, n) ->
                        new EncryptedSecret(rs.getBytes("ciphertext"), rs.getBytes("iv"), rs.getShort("key_version")))
                .optional();
    }

    @Override
    public void save(UserId userId, AiProvider provider, EncryptedApiKey key, Instant verifiedAt) {
        jdbc.sql("""
                        INSERT INTO ai_credential (user_id, provider, ciphertext, iv, key_version, last4, verified_at, created_at,
                            updated_at)
                        VALUES (:userId, :provider, :ciphertext, :iv, :keyVersion, :last4, :now, :now, :now)
                        ON CONFLICT (user_id, provider) DO UPDATE SET
                            ciphertext = EXCLUDED.ciphertext, iv = EXCLUDED.iv, key_version = EXCLUDED.key_version,
                            last4 = EXCLUDED.last4, verified_at = EXCLUDED.verified_at, updated_at = EXCLUDED.updated_at
                        """)
                .param("userId", userId.value())
                .param("provider", provider.name())
                .param("ciphertext", key.secret().ciphertext())
                .param("iv", key.secret().iv())
                .param("keyVersion", key.secret().keyVersion())
                .param("last4", key.last4())
                .param("now", Timestamp.from(verifiedAt))
                .update();
    }

    @Override
    public boolean delete(UserId userId, AiProvider provider) {
        return jdbc.sql("DELETE FROM ai_credential WHERE user_id = :userId AND provider = :provider")
                        .param("userId", userId.value())
                        .param("provider", provider.name())
                        .update()
                > 0;
    }

    @Override
    public Optional<AiProvider> selectedProvider(UserId userId) {
        return jdbc.sql(
                        "SELECT selected_ai_provider FROM app_user WHERE id = :userId AND selected_ai_provider IS NOT NULL")
                .param("userId", userId.value())
                .query(String.class)
                .optional()
                .map(AiProvider::valueOf);
    }

    @Override
    public void select(UserId userId, AiProvider provider) {
        jdbc.sql("UPDATE app_user SET selected_ai_provider = :provider WHERE id = :userId")
                .param("userId", userId.value())
                .param("provider", provider == null ? null : provider.name())
                .update();
    }
}
