package com.stockmanagement.portfolio.application;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.portfolio.application.port.PortfolioStore;
import com.stockmanagement.portfolio.domain.AccountLimitPolicy;
import com.stockmanagement.portfolio.domain.AccountName;
import com.stockmanagement.user.application.port.UserStore;
import com.stockmanagement.user.domain.UserId;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 생성·이름 변경·삭제(FR-002, FR-002a, FR-008). */
@Service
public class AccountService {

    private final PortfolioStore store;
    private final UserStore users;
    private final Clock clock;
    private final AccountLimitPolicy limitPolicy;

    public AccountService(
            PortfolioStore store,
            UserStore users,
            Clock clock,
            @Value("${app.portfolio.max-accounts:2}") int maxAccounts) {
        this.store = store;
        this.users = users;
        this.clock = clock;
        this.limitPolicy = new AccountLimitPolicy(maxAccounts);
    }

    @Transactional
    public long create(UserId userId, String rawName) {
        AccountName name = AccountName.of(rawName);
        users.lock(userId);
        limitPolicy.checkCanAdd(store.countActiveAccounts(userId));
        try {
            return store.insertAccount(userId, name, clock.instant());
        } catch (AccountNameTakenException e) {
            throw new DomainValidationException("name", "account.name.duplicate");
        }
    }

    @Transactional
    public void rename(UserId userId, long accountId, String rawName) {
        AccountName name = AccountName.of(rawName);
        boolean renamed;
        try {
            renamed = store.renameAccount(userId, accountId, name, clock.instant());
        } catch (AccountNameTakenException e) {
            throw new DomainValidationException("name", "account.name.duplicate");
        }
        if (!renamed) {
            throw new ResourceNotFoundException("account", accountId);
        }
    }

    /** 계좌를 소프트 삭제하고 그 안의 보유 종목을 삭제한다. */
    @Transactional
    public void delete(UserId userId, long accountId) {
        if (!store.softDeleteAccount(userId, accountId, clock.instant())) {
            throw new ResourceNotFoundException("account", accountId);
        }
    }
}
