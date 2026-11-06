package com.stockmanagement.portfolio.application.port;

import com.stockmanagement.portfolio.application.AccountNameTakenException;
import com.stockmanagement.portfolio.application.HoldingRef;
import com.stockmanagement.portfolio.domain.AccountName;
import com.stockmanagement.portfolio.domain.HoldingTerms;
import com.stockmanagement.user.domain.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * 계좌·보유 종목 저장소 포트. 모든 메서드는 사용자 범위 안에서만 동작한다: 다른 사용자의 계좌·보유 종목은 없는 것과 같다.
 * "활성 계좌"는 삭제되지 않은 계좌다.
 */
public interface PortfolioStore {

    boolean activeAccountExists(UserId userId, long accountId);

    /** 이름 중복(같은 사용자 안)이면 {@link AccountNameTakenException}. */
    long insertAccount(UserId userId, AccountName name, Instant now);

    /** 이름 중복이면 {@link AccountNameTakenException}. 활성 계좌가 없으면 false. */
    boolean renameAccount(UserId userId, long accountId, AccountName name, Instant now);

    /** 소프트 삭제 + 하위 보유 종목 물리 삭제. 활성 계좌가 없으면 false. */
    boolean softDeleteAccount(UserId userId, long accountId, Instant now);

    Optional<HoldingRef> findHolding(UserId userId, long holdingId);

    Optional<Long> findHoldingId(UserId userId, long accountId, long securityId);

    /** 사용자의 활성 계좌들에 있는 보유 종목 수. */
    long countHoldings(UserId userId);

    /** 사용자의 삭제되지 않은 계좌 수. */
    long countActiveAccounts(UserId userId);

    /** 호출 전에 계좌가 이 사용자의 활성 계좌인지 확인한다. */
    long insertHolding(long accountId, long securityId, HoldingTerms terms, Instant now);

    /** 호출 전에 보유 종목이 이 사용자 것인지 확인한다. */
    void updateHolding(long holdingId, HoldingTerms terms, Instant now);

    boolean deleteHolding(UserId userId, long holdingId);
}
