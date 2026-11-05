package com.stockmanagement.portfolio.persistence;

import com.stockmanagement.portfolio.application.AccountNameTakenException;
import com.stockmanagement.portfolio.application.HoldingRef;
import com.stockmanagement.portfolio.application.port.PortfolioStore;
import com.stockmanagement.portfolio.domain.AccountName;
import com.stockmanagement.portfolio.domain.HoldingTerms;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPortfolioStore implements PortfolioStore {

    private final JdbcClient jdbc;

    JdbcPortfolioStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean activeAccountExists(long accountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id = :id AND deleted_at IS NULL)")
                .param("id", accountId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public long insertAccount(AccountName name, Instant now) {
        try {
            return jdbc.sql(
                            "INSERT INTO account (name, created_at, updated_at) VALUES (:name, :now, :now) RETURNING id")
                    .param("name", name.value())
                    .param("now", Timestamp.from(now))
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new AccountNameTakenException(e);
        }
    }

    @Override
    public boolean renameAccount(long accountId, AccountName name, Instant now) {
        try {
            return jdbc.sql("UPDATE account SET name = :name, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
                            .param("name", name.value())
                            .param("now", Timestamp.from(now))
                            .param("id", accountId)
                            .update()
                    == 1;
        } catch (DuplicateKeyException e) {
            throw new AccountNameTakenException(e);
        }
    }

    @Override
    public boolean softDeleteAccount(long accountId, Instant now) {
        int updated = jdbc.sql(
                        "UPDATE account SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
                .param("now", Timestamp.from(now))
                .param("id", accountId)
                .update();
        if (updated == 0) {
            return false;
        }
        jdbc.sql("DELETE FROM holding WHERE account_id = :id")
                .param("id", accountId)
                .update();
        return true;
    }

    @Override
    public Optional<HoldingRef> findHolding(long holdingId) {
        return jdbc.sql("""
                        SELECT h.id, h.account_id, h.security_id FROM holding h
                        JOIN account a ON a.id = h.account_id AND a.deleted_at IS NULL
                        WHERE h.id = :id
                        """)
                .param("id", holdingId)
                .query((rs, n) -> new HoldingRef(rs.getLong("id"), rs.getLong("account_id"), rs.getLong("security_id")))
                .optional();
    }

    @Override
    public Optional<Long> findHoldingId(long accountId, long securityId) {
        return jdbc.sql("SELECT id FROM holding WHERE account_id = :accountId AND security_id = :securityId")
                .param("accountId", accountId)
                .param("securityId", securityId)
                .query(Long.class)
                .optional();
    }

    @Override
    public long countHoldings() {
        return jdbc.sql("SELECT count(*) FROM holding").query(Long.class).single();
    }

    @Override
    public long insertHolding(long accountId, long securityId, HoldingTerms terms, Instant now) {
        return jdbc.sql("""
                        INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
                        VALUES (:accountId, :securityId, :avgPrice, :quantity, :now, :now)
                        RETURNING id
                        """)
                .param("accountId", accountId)
                .param("securityId", securityId)
                .param("avgPrice", terms.avgPrice().amount())
                .param("quantity", terms.quantity())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single();
    }

    @Override
    public void updateHolding(long holdingId, HoldingTerms terms, Instant now) {
        jdbc.sql("UPDATE holding SET avg_price = :avgPrice, quantity = :quantity, updated_at = :now WHERE id = :id")
                .param("avgPrice", terms.avgPrice().amount())
                .param("quantity", terms.quantity())
                .param("now", Timestamp.from(now))
                .param("id", holdingId)
                .update();
    }

    @Override
    public boolean deleteHolding(long holdingId) {
        return jdbc.sql("""
                                DELETE FROM holding h USING account a
                                WHERE h.id = :id AND a.id = h.account_id AND a.deleted_at IS NULL
                                """).param("id", holdingId).update() == 1;
    }
}
