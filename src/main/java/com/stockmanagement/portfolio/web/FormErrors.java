package com.stockmanagement.portfolio.web;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.portfolio.domain.DuplicateHoldingException;
import com.stockmanagement.portfolio.domain.HoldingLimitExceededException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;

/** 검증 실패 응답: 422 + 필드 오류 모델(contracts/web-routes.md 공통 오류 표). */
final class FormErrors {

    private FormErrors() {}

    static void apply(DomainValidationException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
        model.addAttribute("errorField", e.getField());
        model.addAttribute("errorKey", e.getMessageKey());
        if (e instanceof HoldingLimitExceededException limit) {
            model.addAttribute("errorArg", limit.getMaxHoldings());
        }
        if (e instanceof DuplicateHoldingException duplicate) {
            model.addAttribute("existingHoldingId", duplicate.getExistingHoldingId());
        }
    }

    /** htmx가 오류 폼으로 원래 폼을 바꾸도록 대상과 방식을 다시 지정한다. */
    static void retarget(HttpServletResponse response, String selector) {
        response.setHeader("HX-Retarget", selector);
        response.setHeader("HX-Reswap", "outerHTML");
    }

    static void toast(Model model, String key) {
        model.addAttribute("toastKey", key);
        model.addAttribute("toastLevel", "info");
    }
}
