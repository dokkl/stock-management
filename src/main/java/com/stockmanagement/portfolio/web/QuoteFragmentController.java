package com.stockmanagement.portfolio.web;

import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.user.domain.UserId;
import com.stockmanagement.user.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 대시보드 시세 셀 갱신(GET /fragments/quotes, htmx 30초 폴링). 시세는 스케줄러가 DB에 저장해 두므로 외부 API를
 * 기다리지 않는다(research R3). 평가손익도 함께 바뀌므로 보유 종목 화면을 담당하는 portfolio에 둔다.
 */
@Controller
class QuoteFragmentController {

    private final DashboardQuery dashboardQuery;

    QuoteFragmentController(DashboardQuery dashboardQuery) {
        this.dashboardQuery = dashboardQuery;
    }

    @GetMapping("/fragments/quotes")
    String quotes(@CurrentUser UserId userId, Model model) {
        model.addAttribute("dashboard", dashboardQuery.load(userId));
        return "fragments/quote-cell :: quotes";
    }
}
