package com.stockmanagement.portfolio.application;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.market.application.port.SecurityCatalog;
import com.stockmanagement.market.domain.Security;
import com.stockmanagement.portfolio.application.port.PortfolioStore;
import com.stockmanagement.portfolio.domain.DuplicateHoldingException;
import com.stockmanagement.portfolio.domain.HoldingLimitPolicy;
import com.stockmanagement.portfolio.domain.HoldingTerms;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보유 종목 등록·수정·삭제(FR-003, FR-003a, FR-006, FR-007). */
@Service
public class HoldingService {

    private final PortfolioStore store;
    private final SecurityCatalog securities;
    private final HoldingLimitPolicy limitPolicy;
    private final Clock clock;

    public HoldingService(
            PortfolioStore store,
            SecurityCatalog securities,
            Clock clock,
            @Value("${app.portfolio.max-holdings:500}") int maxHoldings) {
        this.store = store;
        this.securities = securities;
        this.clock = clock;
        this.limitPolicy = new HoldingLimitPolicy(maxHoldings);
    }

    public int maxHoldings() {
        return limitPolicy.maxHoldings();
    }

    @Transactional
    public long add(long accountId, Long securityId, BigDecimal avgPrice, BigDecimal quantity) {
        if (!store.activeAccountExists(accountId)) {
            throw new ResourceNotFoundException("account", accountId);
        }
        Security security = requireSecurity(securityId);
        HoldingTerms terms = HoldingTerms.of(security, avgPrice, quantity);
        store.findHoldingId(accountId, security.id()).ifPresent(existing -> {
            throw new DuplicateHoldingException(existing);
        });
        limitPolicy.checkCanAdd(store.countHoldings());
        try {
            return store.insertHolding(accountId, security.id(), terms, clock.instant());
        } catch (DuplicateKeyException e) {
            // 동시 등록 경쟁: 트랜잭션이 중단되었으므로 기존 id를 다시 조회하지 않는다.
            throw new DomainValidationException("securityId", "holding.duplicate");
        }
    }

    @Transactional
    public void update(long holdingId, BigDecimal avgPrice, BigDecimal quantity) {
        HoldingRef ref =
                store.findHolding(holdingId).orElseThrow(() -> new ResourceNotFoundException("holding", holdingId));
        Security security = requireSecurity(ref.securityId());
        store.updateHolding(holdingId, HoldingTerms.of(security, avgPrice, quantity), clock.instant());
    }

    @Transactional
    public void delete(long holdingId) {
        if (!store.deleteHolding(holdingId)) {
            throw new ResourceNotFoundException("holding", holdingId);
        }
    }

    private Security requireSecurity(Long securityId) {
        if (securityId == null) {
            throw new DomainValidationException("securityId", "holding.security.required");
        }
        return securities
                .findById(securityId)
                .orElseThrow(() -> new DomainValidationException("securityId", "holding.security.required"));
    }
}
