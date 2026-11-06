package com.stockmanagement.guide.web;

import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.guide.application.GuideRequestResult;
import com.stockmanagement.guide.application.GuideService;
import com.stockmanagement.guide.application.GuideView;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.user.domain.UserId;
import com.stockmanagement.user.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
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
    static final String GUIDE_READY = "guide-ready";

    private final GuideService guideService;
    private final DashboardQuery dashboardQuery;
    private final Clock clock;

    GuideController(GuideService guideService, DashboardQuery dashboardQuery, Clock clock) {
        this.guideService = guideService;
        this.dashboardQuery = dashboardQuery;
        this.clock = clock;
    }

    @PostMapping("/holdings/{id}/guides")
    String request(
            @CurrentUser UserId userId,
            @PathVariable long id,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        return switch (guideService.request(userId, id)) {
            case GuideRequestResult.Accepted accepted -> {
                GuideView guide = requireGuide(userId, accepted.guideId());
                response.setStatus(HttpStatus.ACCEPTED.value());
                quotaChanged(response, guide.securityId());
                progress(model, guide);
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

    /** 진행 중이면 204: 진행 카드(애니메이션)를 그대로 두고 폴링만 계속한다. 끝나면 결과·실패 카드로 바꾼다. */
    @GetMapping("/guides/{id}/status")
    String status(@CurrentUser UserId userId, @PathVariable long id, Model model, HttpServletResponse response) {
        GuideView guide = requireGuide(userId, id);
        if (guide.isPending()) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return "guide/progress :: none";
        }
        model.addAttribute("guide", guide);
        quotaChanged(response, guide.securityId(), guide);
        return guide.isSucceeded() ? "guide/result :: result" : "guide/failure :: failure";
    }

    /** 서랍 패널을 다시 열 때 쓰는 카드(완료 알림의 "보기"). 상태에 맞는 카드만 돌려주고 이벤트는 보내지 않는다. */
    @GetMapping("/guides/{id}/card")
    String card(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        GuideView guide = requireGuide(userId, id);
        progress(model, guide);
        if (guide.isPending()) {
            return "guide/progress :: progress";
        }
        return guide.isSucceeded() ? "guide/result :: result" : "guide/failure :: failure";
    }

    @GetMapping("/guides/{id}")
    String detail(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        progress(model, requireGuide(userId, id));
        return "guide/detail";
    }

    /** 진행 카드는 요청 후 지난 시간부터 단계 표시를 이어 간다(다시 열어도 처음부터 시작하지 않게). */
    private void progress(Model model, GuideView guide) {
        model.addAttribute("guide", guide);
        model.addAttribute(
                "elapsedSeconds",
                Math.max(
                        0,
                        Duration.between(guide.requestedAt(), clock.instant()).toSeconds()));
    }

    /** 가이드 버튼(남은 횟수) 다시 그리기. */
    @GetMapping("/holdings/{id}/guide-button")
    String button(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        model.addAttribute(
                "holding",
                dashboardQuery.findHolding(userId, id).orElseThrow(() -> new ResourceNotFoundException("holding", id)));
        return "guide/button :: button-only";
    }

    private GuideView requireGuide(UserId userId, long id) {
        return guideService.find(userId, id).orElseThrow(() -> new ResourceNotFoundException("guide", id));
    }

    /** 공통 이벤트와 종목별 이벤트를 함께 보낸다. 버튼은 자기 종목 이벤트만 듣는다(같은 종목의 다른 계좌 행 포함). */
    private static void quotaChanged(HttpServletResponse response, long securityId) {
        response.setHeader("HX-Trigger", QUOTA_CHANGED + ", " + QUOTA_CHANGED + "-" + securityId);
    }

    /**
     * 완료(성공·실패) 시에는 버튼 갱신 이벤트에 더해 {@code guide-ready} 이벤트를 보낸다. 서랍 패널이 닫혀 있으면 화면이
     * 토스트로 알린다. 헤더에는 한글을 실을 수 없으므로 ID만 보내고 종목 이름은 화면에서 찾는다.
     */
    private static void quotaChanged(HttpServletResponse response, long securityId, GuideView guide) {
        response.setHeader(
                "HX-Trigger",
                "{\"" + QUOTA_CHANGED + "\":\"\",\"" + QUOTA_CHANGED + "-" + securityId + "\":\"\","
                        + "\"" + GUIDE_READY + "\":{\"id\":" + guide.id() + ",\"securityId\":" + securityId
                        + ",\"ok\":" + guide.isSucceeded() + "}}");
    }
}
