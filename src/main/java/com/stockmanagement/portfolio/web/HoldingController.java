package com.stockmanagement.portfolio.web;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.portfolio.application.DashboardView.HoldingView;
import com.stockmanagement.portfolio.application.HoldingService;
import com.stockmanagement.user.domain.UserId;
import com.stockmanagement.user.web.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 보유 종목 경로(contracts/web-routes.md). 응답은 htmx 프래그먼트다. */
@Controller
class HoldingController {

    private final HoldingService holdingService;
    private final DashboardQuery dashboardQuery;

    HoldingController(HoldingService holdingService, DashboardQuery dashboardQuery) {
        this.holdingService = holdingService;
        this.dashboardQuery = dashboardQuery;
    }

    @PostMapping("/accounts/{accountId}/holdings")
    String create(
            @CurrentUser UserId userId,
            @PathVariable long accountId,
            @RequestParam(required = false) String securityId,
            @RequestParam(required = false) String securityLabel,
            @RequestParam(required = false) String avgPrice,
            @RequestParam(required = false) String quantity,
            Model model,
            HttpServletResponse response) {
        HoldingForm form = new HoldingForm(accountId, parseId(securityId), securityLabel, avgPrice, quantity);
        try {
            long id = holdingService.add(
                    userId, accountId, form.securityId(), form.avgPriceValue(), form.quantityValue());
            response.setStatus(HttpStatus.CREATED.value());
            model.addAttribute("holding", requireHolding(userId, id));
            return "portfolio/holding :: created";
        } catch (DomainValidationException e) {
            FormErrors.apply(e, model, response);
            FormErrors.retarget(response, "#holding-form-" + accountId);
            model.addAttribute("form", form);
            model.addAttribute("accountId", accountId);
            return "portfolio/holding :: form-only";
        }
    }

    @GetMapping("/holdings/{id}")
    String row(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        model.addAttribute("holding", requireHolding(userId, id));
        return "portfolio/holding :: row-only";
    }

    @GetMapping("/holdings/{id}/edit")
    String editRow(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        model.addAttribute("holding", requireHolding(userId, id));
        return "portfolio/holding :: edit-row-only";
    }

    @PutMapping("/holdings/{id}")
    String update(
            @CurrentUser UserId userId,
            @PathVariable long id,
            @RequestParam(required = false) String avgPrice,
            @RequestParam(required = false) String quantity,
            Model model,
            HttpServletResponse response) {
        HoldingForm form = new HoldingForm(null, null, null, avgPrice, quantity);
        try {
            holdingService.update(userId, id, form.avgPriceValue(), form.quantityValue());
        } catch (DomainValidationException e) {
            FormErrors.apply(e, model, response);
            model.addAttribute("form", form);
            model.addAttribute("holding", requireHolding(userId, id));
            return "portfolio/holding :: edit-row-only";
        }
        model.addAttribute("holding", requireHolding(userId, id));
        return "portfolio/holding :: row-only";
    }

    @DeleteMapping("/holdings/{id}")
    String delete(@CurrentUser UserId userId, @PathVariable long id, Model model) {
        holdingService.delete(userId, id);
        FormErrors.toast(model, "portfolio.holding.deleted");
        return "fragments/toast :: toast";
    }

    private HoldingView requireHolding(UserId userId, long id) {
        return dashboardQuery.findHolding(userId, id).orElseThrow(() -> new ResourceNotFoundException("holding", id));
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
