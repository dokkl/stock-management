package com.stockmanagement.portfolio.web;

import com.stockmanagement.portfolio.application.DashboardQuery;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 계좌별 보유 목록 대시보드(GET /). */
@Controller
class DashboardController {

    private final DashboardQuery dashboardQuery;

    DashboardController(DashboardQuery dashboardQuery) {
        this.dashboardQuery = dashboardQuery;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardQuery.load());
        return "portfolio/dashboard";
    }
}
