package com.stockmanagement.market.application.port;

import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.Security;
import com.stockmanagement.market.domain.SecurityListing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 종목 마스터 저장소 포트. */
public interface SecurityCatalog {

    Optional<Security> findById(long id);

    /** 상장폐지 제외, 코드/티커 prefix 또는 이름 유사도 검색. market이 null이면 전체. */
    List<Security> search(String query, Market market, int limit);

    boolean isEmpty();

    /** 마스터 항목을 upsert한다. 상장폐지였던 종목이 다시 나오면 ACTIVE로 되돌린다. */
    void upsert(List<SecurityListing> listings, Instant syncedAt);

    /** syncedAt 이전에 갱신된(이번 동기화에 없던) 종목을 DELISTED로 표시한다. */
    int delistMissing(Exchange exchange, Instant syncedAt);
}
