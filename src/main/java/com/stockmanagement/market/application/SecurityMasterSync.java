package com.stockmanagement.market.application;

import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.application.port.SecurityCatalog;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.SecurityListing;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 종목 마스터 동기화: 매일 06:00 KST, 그리고 기동 시 종목 테이블이 비어 있으면 실행한다.
 * 거래소별로 독립 처리하며, 다운로드에 실패한 거래소는 상장폐지 처리하지 않는다.
 */
@Service
public class SecurityMasterSync {

    private static final Logger log = LoggerFactory.getLogger(SecurityMasterSync.class);

    private final MarketDataProvider provider;
    private final SecurityCatalog catalog;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final TaskExecutor executor;
    private final boolean syncOnStartup;

    public SecurityMasterSync(
            MarketDataProvider provider,
            SecurityCatalog catalog,
            TransactionTemplate transactions,
            Clock clock,
            @Qualifier("guideExecutor") TaskExecutor executor,
            @Value("${app.market.master-sync-on-startup:true}") boolean syncOnStartup) {
        this.provider = provider;
        this.catalog = catalog;
        this.transactions = transactions;
        this.clock = clock;
        this.executor = executor;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncIfEmptyOnStartup() {
        if (syncOnStartup && catalog.isEmpty()) {
            executor.execute(this::syncAll);
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void syncAll() {
        for (Exchange exchange : Exchange.values()) {
            syncExchange(exchange);
        }
    }

    private void syncExchange(Exchange exchange) {
        List<SecurityListing> listings;
        try {
            listings = provider.fetchSecurityMaster(exchange).stream()
                    .filter(SecurityListing::isStorable)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("종목 마스터 다운로드 실패, 이번 동기화에서 제외: exchange={}", exchange, e);
            return;
        }
        if (listings.isEmpty()) {
            log.warn("종목 마스터가 비어 있어 상장폐지 처리를 건너뜀: exchange={}", exchange);
            return;
        }
        Instant syncedAt = clock.instant();
        Integer delisted = transactions.execute(status -> {
            catalog.upsert(listings, syncedAt);
            return catalog.delistMissing(exchange, syncedAt);
        });
        log.info("종목 마스터 동기화: exchange={}, listings={}, delisted={}", exchange, listings.size(), delisted);
    }
}
