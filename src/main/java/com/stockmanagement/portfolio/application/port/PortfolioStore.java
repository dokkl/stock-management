package com.stockmanagement.portfolio.application.port;

import com.stockmanagement.portfolio.application.AccountNameTakenException;
import com.stockmanagement.portfolio.application.HoldingRef;
import com.stockmanagement.portfolio.domain.AccountName;
import com.stockmanagement.portfolio.domain.HoldingTerms;
import java.time.Instant;
import java.util.Optional;

/** 계좌·보유 종목 저장소 포트. "활성 계좌"는 삭제되지 않은 계좌다. */
public interface PortfolioStore {

    boolean activeAccountExists(long accountId);

    /** 이름 중복이면 {@link AccountNameTakenException}. */
    long insertAccount(AccountName name, Instant now);

    /** 이름 중복이면 {@link AccountNameTakenException}. 활성 계좌가 없으면 false. */
    boolean renameAccount(long accountId, AccountName name, Instant now);

    /** 소프트 삭제 + 하위 보유 종목 물리 삭제. 활성 계좌가 없으면 false. */
    boolean softDeleteAccount(long accountId, Instant now);

    Optional<HoldingRef> findHolding(long holdingId);

    Optional<Long> findHoldingId(long accountId, long securityId);

    long countHoldings();

    /** 삭제되지 않은 계좌 수. */
    long countActiveAccounts();

    long insertHolding(long accountId, long securityId, HoldingTerms terms, Instant now);

    void updateHolding(long holdingId, HoldingTerms terms, Instant now);

    boolean deleteHolding(long holdingId);
}
