package com.stockmanagement.portfolio.application;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.portfolio.application.port.PortfolioStore;
import com.stockmanagement.portfolio.domain.AccountName;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 생성·이름 변경·삭제(FR-002, FR-008). */
@Service
public class AccountService {

    private final PortfolioStore store;
    private final Clock clock;

    public AccountService(PortfolioStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public long create(String rawName) {
        AccountName name = AccountName.of(rawName);
        try {
            return store.insertAccount(name, clock.instant());
        } catch (AccountNameTakenException e) {
            throw new DomainValidationException("name", "account.name.duplicate");
        }
    }

    @Transactional
    public void rename(long accountId, String rawName) {
        AccountName name = AccountName.of(rawName);
        boolean renamed;
        try {
            renamed = store.renameAccount(accountId, name, clock.instant());
        } catch (AccountNameTakenException e) {
            throw new DomainValidationException("name", "account.name.duplicate");
        }
        if (!renamed) {
            throw new ResourceNotFoundException("account", accountId);
        }
    }

    /** 계좌를 소프트 삭제하고 그 안의 보유 종목을 삭제한다. */
    @Transactional
    public void delete(long accountId) {
        if (!store.softDeleteAccount(accountId, clock.instant())) {
            throw new ResourceNotFoundException("account", accountId);
        }
    }
}
