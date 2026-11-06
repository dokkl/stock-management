package com.stockmanagement.market.web;

import com.stockmanagement.market.application.MarketIndicatorQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 대시보드 시장 지표 영역(003 contracts/web-routes.md). 대시보드가 처음 불러오고(load) 60초마다 다시 받는다.
 * 저장된 값만 읽으므로 외부 제공처 장애가 화면을 막지 않는다. 공유 데이터라 사용자 범위가 없다.
 */
@Controller
class MarketIndicatorController {

    private static final Logger log = LoggerFactory.getLogger(MarketIndicatorController.class);

    private final MarketIndicatorQuery query;

    MarketIndicatorController(MarketIndicatorQuery query) {
        this.query = query;
    }

    @GetMapping("/fragments/indicators")
    String indicators(Model model) {
        try {
            model.addAttribute("board", query.board());
            model.addAttribute("indicatorError", false);
        } catch (DataAccessException e) {
            log.warn("시장 지표 조회 실패: type={}", e.getClass().getSimpleName());
            model.addAttribute("indicatorError", true);
        }
        return "market/indicators :: indicators";
    }
}
