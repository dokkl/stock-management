package com.stockmanagement.portfolio.web;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.portfolio.application.AccountService;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.portfolio.application.DashboardView.AccountView;
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

/** 계좌 경로(contracts/web-routes.md). 응답은 htmx 프래그먼트다. */
@Controller
class AccountController {

    private final AccountService accountService;
    private final DashboardQuery dashboardQuery;

    AccountController(AccountService accountService, DashboardQuery dashboardQuery) {
        this.accountService = accountService;
        this.dashboardQuery = dashboardQuery;
    }

    @PostMapping("/accounts")
    String create(@RequestParam(defaultValue = "") String name, Model model, HttpServletResponse response) {
        try {
            long id = accountService.create(name);
            response.setStatus(HttpStatus.CREATED.value());
            model.addAttribute("account", requireAccount(id));
            return "portfolio/account :: created";
        } catch (DomainValidationException e) {
            FormErrors.apply(e, model, response);
            FormErrors.retarget(response, "#account-create-form");
            model.addAttribute("accountNameValue", name);
            return "portfolio/account-create-form :: create-form-only";
        }
    }

    @GetMapping("/accounts/{id}/edit")
    String renameForm(@PathVariable long id, Model model) {
        model.addAttribute("account", requireAccount(id));
        return "portfolio/account :: rename-form";
    }

    @GetMapping("/accounts/{id}/header")
    String header(@PathVariable long id, Model model) {
        model.addAttribute("account", requireAccount(id));
        return "portfolio/account :: header-only";
    }

    @PutMapping("/accounts/{id}")
    String rename(
            @PathVariable long id,
            @RequestParam(defaultValue = "") String name,
            Model model,
            HttpServletResponse response) {
        try {
            accountService.rename(id, name);
        } catch (DomainValidationException e) {
            FormErrors.apply(e, model, response);
            model.addAttribute("account", requireAccount(id));
            model.addAttribute("accountNameValue", name);
            return "portfolio/account :: rename-form";
        }
        model.addAttribute("account", requireAccount(id));
        return "portfolio/account :: header-only";
    }

    @DeleteMapping("/accounts/{id}")
    String delete(@PathVariable long id, Model model) {
        accountService.delete(id);
        FormErrors.toast(model, "portfolio.account.deleted");
        model.addAttribute("nowEmpty", dashboardQuery.load().isEmpty());
        return "portfolio/account :: deleted";
    }

    private AccountView requireAccount(long id) {
        return dashboardQuery.findAccount(id).orElseThrow(() -> new ResourceNotFoundException("account", id));
    }
}
