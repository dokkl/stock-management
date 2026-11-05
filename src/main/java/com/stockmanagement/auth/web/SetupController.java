package com.stockmanagement.auth.web;

import com.stockmanagement.auth.application.SetupAlreadyCompletedException;
import com.stockmanagement.auth.application.SetupService;
import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 최초 비밀번호 설정. 소유자가 이미 있으면 404 (contracts/web-routes.md). */
@Controller
class SetupController {

    private final SetupService setupService;

    SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/setup")
    String form() {
        requireNotCompleted();
        return "auth/setup";
    }

    @PostMapping("/setup")
    String submit(@RequestParam String password, @RequestParam String passwordConfirm, Model model) {
        requireNotCompleted();
        try {
            setupService.setup(password, passwordConfirm);
        } catch (SetupAlreadyCompletedException e) {
            throw new ResourceNotFoundException("setup", "completed");
        } catch (DomainValidationException e) {
            model.addAttribute("errorField", e.getField());
            model.addAttribute("errorKey", e.getMessageKey());
            return "auth/setup";
        }
        return "redirect:/login";
    }

    private void requireNotCompleted() {
        if (setupService.isCompleted()) {
            throw new ResourceNotFoundException("setup", "completed");
        }
    }
}
