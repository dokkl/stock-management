package com.stockmanagement.guide.web;

import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.guide.application.port.GuideHistoryQuery;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 종목별 가이드 기록(GET /securities/{id}/guides?page=, FR-026). 최신순 20건씩. */
@Controller
class GuideHistoryController {

    private final GuideHistoryQuery historyQuery;

    GuideHistoryController(GuideHistoryQuery historyQuery) {
        this.historyQuery = historyQuery;
    }

    @GetMapping("/securities/{id}/guides")
    String history(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute(
                "history",
                historyQuery.find(id, page).orElseThrow(() -> new ResourceNotFoundException("security", id)));
        return "guide/history";
    }
}
