package com.stockmanagement.market.application;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.market.application.port.SecurityCatalog;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.Security;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종목 검색(FR-004): 검색어 1~40자, 최대 20건. */
@Service
public class SecuritySearch {

    public static final int MAX_QUERY_LENGTH = 40;
    public static final int MAX_RESULTS = 20;

    private final SecurityCatalog catalog;

    public SecuritySearch(SecurityCatalog catalog) {
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<Security> search(String query, Market market) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty() || q.length() > MAX_QUERY_LENGTH) {
            throw new DomainValidationException("q", "security.query.invalid");
        }
        return catalog.search(q, market, MAX_RESULTS);
    }
}
