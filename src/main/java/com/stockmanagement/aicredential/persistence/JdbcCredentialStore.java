package com.stockmanagement.aicredential.persistence;

import com.stockmanagement.aicredential.application.EncryptedApiKey;
import com.stockmanagement.aicredential.application.StoredCredential;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
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
    public List<StoredCredential> findAll() {
        return jdbc.sql("SELECT provider, last4, verified_at FROM ai_credential ORDER BY id")
                .query((rs, n) -> new StoredCredential(
                        AiProvider.valueOf(rs.getString("provider")),
                        rs.getString("last4"),
                        rs.getTimestamp("verified_at").toInstant()))
                .list();
    }

    @Override
    public void save(AiProvider provider, EncryptedApiKey key, Instant verifiedAt) {
        jdbc.sql("""
                        INSERT INTO ai_credential (provider, ciphertext, iv, key_version, last4, verified_at, created_at, updated_at)
                        VALUES (:provider, :ciphertext, :iv, :keyVersion, :last4, :now, :now, :now)
                        ON CONFLICT (provider) DO UPDATE SET
                            ciphertext = EXCLUDED.ciphertext, iv = EXCLUDED.iv, key_version = EXCLUDED.key_version,
                            last4 = EXCLUDED.last4, verified_at = EXCLUDED.verified_at, updated_at = EXCLUDED.updated_at
                        """)
                .param("provider", provider.name())
                .param("ciphertext", key.secret().ciphertext())
                .param("iv", key.secret().iv())
                .param("keyVersion", key.secret().keyVersion())
                .param("last4", key.last4())
                .param("now", Timestamp.from(verifiedAt))
                .update();
    }

    @Override
    public boolean delete(AiProvider provider) {
        return jdbc.sql("DELETE FROM ai_credential WHERE provider = :provider")
                        .param("provider", provider.name())
                        .update()
                > 0;
    }

    @Override
    public Optional<AiProvider> selectedProvider() {
        return jdbc.sql("SELECT selected_ai_provider FROM owner WHERE id = 1 AND selected_ai_provider IS NOT NULL")
                .query(String.class)
                .optional()
                .map(AiProvider::valueOf);
    }

    @Override
    public void select(AiProvider provider) {
        jdbc.sql("UPDATE owner SET selected_ai_provider = :provider, updated_at = now() WHERE id = 1")
                .param("provider", provider == null ? null : provider.name())
                .update();
    }
}
