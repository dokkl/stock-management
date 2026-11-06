package com.stockmanagement.user.web;

import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.common.web.HtmxSupport;
import com.stockmanagement.user.application.WithdrawalService;
import com.stockmanagement.user.application.port.UserStore;
import com.stockmanagement.user.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** 회원 정보·탈퇴(contracts/web-routes.md "회원 정보·탈퇴"). */
@Controller
class AccountSettingsController {

    private final UserStore users;
    private final WithdrawalService withdrawalService;

    AccountSettingsController(UserStore users, WithdrawalService withdrawalService) {
        this.users = users;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/settings/account")
    String page(@CurrentUser UserId userId, Model model) {
        model.addAttribute(
                "user", users.find(userId).orElseThrow(() -> new ResourceNotFoundException("user", userId.value())));
        return "settings/account";
    }

    /** 데이터 삭제 → 세션 무효화 → 로그인 화면. htmx 요청이면 HX-Redirect로 이동시킨다. */
    @PostMapping("/settings/account/withdraw")
    void withdraw(@CurrentUser UserId userId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        withdrawalService.withdraw(userId);
        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        String target = request.getContextPath() + "/login?withdrawn";
        if (HtmxSupport.isHtmx(request)) {
            HtmxSupport.redirect(response, target);
            return;
        }
        response.sendRedirect(target);
    }
}
