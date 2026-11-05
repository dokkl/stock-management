package com.stockmanagement.auth.web;

import com.stockmanagement.auth.application.LoginAttemptService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class LoginController {

    private final LoginAttemptService loginAttemptService;

    LoginController(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/login")
    String login(@RequestParam(required = false) String locked, Model model) {
        if (locked != null) {
            model.addAttribute("lockMinutes", Math.max(1, loginAttemptService.remainingLockMinutes()));
        }
        return "auth/login";
    }
}
