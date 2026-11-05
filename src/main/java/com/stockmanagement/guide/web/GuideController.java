package com.stockmanagement.guide.web;

import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.guide.application.GuideRequestResult;
import com.stockmanagement.guide.application.GuideService;
import com.stockmanagement.guide.application.GuideView;
import com.stockmanagement.portfolio.application.DashboardQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 가이드 경로(contracts/web-routes.md). 요청은 슬롯을 예약하고 202로 바로 돌아가며, 화면은 2초마다 상태를 폴링한다.
 * 한도가 바뀌면 HX-Trigger로 같은 종목의 가이드 버튼을 다시 그리게 한다.
 */
@Controller
class GuideController {

    static final String QUOTA_CHANGED = "guide-quota-changed";

    private final GuideService guideService;
    private final DashboardQuery dashboardQuery;

    GuideController(GuideService guideService, DashboardQuery dashboardQuery) {
        this.guideService = guideService;
        this.dashboardQuery = dashboardQuery;
    }

    @PostMapping("/holdings/{id}/guides")
    String request(@PathVariable long id, Model model, HttpServletRequest request, HttpServletResponse response) {
        return switch (guideService.request(id)) {
            case GuideRequestResult.Accepted accepted -> {
                GuideView guide = requireGuide(accepted.guideId());
                response.setStatus(HttpStatus.ACCEPTED.value());
                quotaChanged(response, guide.securityId());
                model.addAttribute("guide", guide);
                yield "guide/progress :: progress";
            }
            case GuideRequestResult.NoCredential noCredential -> {
                response.setStatus(HttpStatus.CONFLICT.value());
                response.setHeader("HX-Redirect", request.getContextPath() + "/settings/ai");
                yield "guide/failure :: no-credential";
            }
            case GuideRequestResult.QuotaExhausted exhausted -> {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                model.addAttribute("resetsAt", exhausted.resetsAt());
                yield "guide/quota-exhausted :: quota-exhausted";
            }
            case GuideRequestResult.Unavailable unavailable -> {
                response.setStatus(HttpStatus.CONFLICT.value());
                model.addAttribute("reason", unavailable.reason().name());
                yield "guide/failure :: unavailable";
            }
        };
    }

    @GetMapping("/guides/{id}/status")
    String status(@PathVariable long id, Model model, HttpServletResponse response) {
        GuideView guide = requireGuide(id);
        model.addAttribute("guide", guide);
        if (guide.isPending()) {
            return "guide/progress :: progress";
        }
        quotaChanged(response, guide.securityId());
        return guide.isSucceeded() ? "guide/result :: result" : "guide/failure :: failure";
    }

    @GetMapping("/guides/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("guide", requireGuide(id));
        return "guide/detail";
    }

    /** 가이드 버튼(남은 횟수) 다시 그리기. */
    @GetMapping("/holdings/{id}/guide-button")
    String button(@PathVariable long id, Model model) {
        model.addAttribute(
                "holding",
                dashboardQuery.findHolding(id).orElseThrow(() -> new ResourceNotFoundException("holding", id)));
        return "guide/button :: button-only";
    }

    private GuideView requireGuide(long id) {
        return guideService.find(id).orElseThrow(() -> new ResourceNotFoundException("guide", id));
    }

    /** 공통 이벤트와 종목별 이벤트를 함께 보낸다. 버튼은 자기 종목 이벤트만 듣는다(같은 종목의 다른 계좌 행 포함). */
    private static void quotaChanged(HttpServletResponse response, long securityId) {
        response.setHeader("HX-Trigger", QUOTA_CHANGED + ", " + QUOTA_CHANGED + "-" + securityId);
    }
}
