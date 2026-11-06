package com.stockmanagement.portfolio.persistence;

import com.stockmanagement.portfolio.application.AccountNameTakenException;
import com.stockmanagement.portfolio.application.HoldingRef;
import com.stockmanagement.portfolio.application.port.PortfolioStore;
import com.stockmanagement.portfolio.domain.AccountName;
import com.stockmanagement.portfolio.domain.HoldingTerms;
import com.stockmanagement.user.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 모든 조회·변경에 account.user_id 조건을 붙인다(research R4). holding은 account를 통해 소유된다. */
@Repository
class JdbcPortfolioStore implements PortfolioStore {

    private final JdbcClient jdbc;

    JdbcPortfolioStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean activeAccountExists(UserId userId, long accountId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM account WHERE id = :id AND user_id = :userId AND deleted_at IS NULL)
                        """)
                .param("id", accountId)
                .param("userId", userId.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public long insertAccount(UserId userId, AccountName name, Instant now) {
        try {
            return jdbc.sql("""
                            INSERT INTO account (user_id, name, created_at, updated_at)
                            VALUES (:userId, :name, :now, :now) RETURNING id
                            """)
                    .param("userId", userId.value())
                    .param("name", name.value())
                    .param("now", Timestamp.from(now))
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new AccountNameTakenException(e);
        }
    }

    @Override
    public boolean renameAccount(UserId userId, long accountId, AccountName name, Instant now) {
        try {
            return jdbc.sql("""
                                    UPDATE account SET name = :name, updated_at = :now
                                    WHERE id = :id AND user_id = :userId AND deleted_at IS NULL
                                    """)
                            .param("name", name.value())
                            .param("now", Timestamp.from(now))
                            .param("id", accountId)
                            .param("userId", userId.value())
                            .update()
                    == 1;
        } catch (DuplicateKeyException e) {
            throw new AccountNameTakenException(e);
        }
    }

    @Override
    public boolean softDeleteAccount(UserId userId, long accountId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE account SET deleted_at = :now, updated_at = :now
                        WHERE id = :id AND user_id = :userId AND deleted_at IS NULL
                        """)
                .param("now", Timestamp.from(now))
                .param("id", accountId)
                .param("userId", userId.value())
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
    public Optional<HoldingRef> findHolding(UserId userId, long holdingId) {
        return jdbc.sql("""
                        SELECT h.id, h.account_id, h.security_id FROM holding h
                        JOIN account a ON a.id = h.account_id AND a.deleted_at IS NULL AND a.user_id = :userId
                        WHERE h.id = :id
                        """)
                .param("id", holdingId)
                .param("userId", userId.value())
                .query((rs, n) -> new HoldingRef(rs.getLong("id"), rs.getLong("account_id"), rs.getLong("security_id")))
                .optional();
    }

    @Override
    public Optional<Long> findHoldingId(UserId userId, long accountId, long securityId) {
        return jdbc.sql("""
                        SELECT h.id FROM holding h JOIN account a ON a.id = h.account_id AND a.user_id = :userId
                        WHERE h.account_id = :accountId AND h.security_id = :securityId
                        """)
                .param("userId", userId.value())
                .param("accountId", accountId)
                .param("securityId", securityId)
                .query(Long.class)
                .optional();
    }

    @Override
    public long countHoldings(UserId userId) {
        return jdbc.sql("""
                        SELECT count(*) FROM holding h
                        JOIN account a ON a.id = h.account_id AND a.deleted_at IS NULL AND a.user_id = :userId
                        """).param("userId", userId.value()).query(Long.class).single();
    }

    @Override
    public long countActiveAccounts(UserId userId) {
        return jdbc.sql("SELECT count(*) FROM account WHERE user_id = :userId AND deleted_at IS NULL")
                .param("userId", userId.value())
                .query(Long.class)
                .single();
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
    public boolean deleteHolding(UserId userId, long holdingId) {
        return jdbc.sql("""
                                DELETE FROM holding h USING account a
                                WHERE h.id = :id AND a.id = h.account_id AND a.deleted_at IS NULL AND a.user_id = :userId
                                """)
                        .param("id", holdingId)
                        .param("userId", userId.value())
                        .update()
                == 1;
    }
}
