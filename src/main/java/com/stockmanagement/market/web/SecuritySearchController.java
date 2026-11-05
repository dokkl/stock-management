package com.stockmanagement.market.web;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.market.application.SecuritySearch;
import com.stockmanagement.market.domain.Market;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 종목 검색 자동완성 프래그먼트(GET /securities/search). */
@Controller
class SecuritySearchController {

    private final SecuritySearch securitySearch;

    SecuritySearchController(SecuritySearch securitySearch) {
        this.securitySearch = securitySearch;
    }

    @GetMapping("/securities/search")
    String search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Market market,
            Model model,
            HttpServletResponse response) {
        try {
            model.addAttribute("results", securitySearch.search(q, market));
        } catch (DomainValidationException e) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            model.addAttribute("errorKey", e.getMessageKey());
        }
        return "portfolio/security-search-results :: results";
    }
}
