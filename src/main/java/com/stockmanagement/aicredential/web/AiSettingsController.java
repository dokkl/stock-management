package com.stockmanagement.aicredential.web;

import com.stockmanagement.aicredential.application.CredentialCheckUnavailableException;
import com.stockmanagement.aicredential.application.CredentialRejectedException;
import com.stockmanagement.aicredential.application.CredentialService;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI 설정 경로(contracts/web-routes.md). 관리자 전용(SecurityConfig)이며 공용 AI 키를 관리한다.
 * 변경 요청은 설정 패널 프래그먼트(#ai-settings)를 돌려준다.
 * 입력한 키는 어떤 응답에도 다시 넣지 않는다: 오류로 다시 그릴 때도 입력란은 비어 있다(SC-009).
 */
@Controller
class AiSettingsController {

    private static final String PAGE = "settings/ai";
    private static final String PANEL = "settings/ai :: panel";

    private final CredentialService credentialService;

    AiSettingsController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/settings/ai")
    String page(Model model) {
        model.addAttribute("settings", credentialService.settings());
        return PAGE;
    }

    @PutMapping("/settings/ai/credentials/{provider}")
    String register(
            @PathVariable String provider,
            @RequestParam(defaultValue = "") String apiKey,
            Model model,
            HttpServletResponse response) {
        AiProvider target = parse(provider);
        try {
            credentialService.register(target, apiKey);
            toast(model, "settings.ai.saved");
        } catch (DomainValidationException e) {
            keyError(model, response, target, e.getMessageKey(), HttpStatus.UNPROCESSABLE_CONTENT);
        } catch (CredentialRejectedException e) {
            keyError(model, response, target, "ai.key.rejected", HttpStatus.UNPROCESSABLE_CONTENT);
        } catch (CredentialCheckUnavailableException e) {
            keyError(model, response, target, "ai.key.unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return panel(model);
    }

    @DeleteMapping("/settings/ai/credentials/{provider}")
    String delete(@PathVariable String provider, Model model) {
        credentialService.delete(parse(provider));
        toast(model, "settings.ai.deleted");
        return panel(model);
    }

    @PutMapping("/settings/ai/selection")
    String select(@RequestParam(defaultValue = "") String provider, Model model, HttpServletResponse response) {
        try {
            credentialService.select(parseSelection(provider));
            toast(model, "settings.ai.selectionSaved");
        } catch (DomainValidationException e) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            model.addAttribute("selectionErrorKey", e.getMessageKey());
        }
        return panel(model);
    }

    private String panel(Model model) {
        model.addAttribute("settings", credentialService.settings());
        return PANEL;
    }

    private static void keyError(
            Model model, HttpServletResponse response, AiProvider provider, String messageKey, HttpStatus status) {
        response.setStatus(status.value());
        model.addAttribute("errorProvider", provider);
        model.addAttribute("errorKey", messageKey);
    }

    private static void toast(Model model, String key) {
        model.addAttribute("toastKey", key);
        model.addAttribute("toastLevel", "info");
    }

    private static AiProvider parse(String value) {
        return Arrays.stream(AiProvider.values())
                .filter(p -> p.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("aiProvider", value));
    }

    /** 선택 폼의 값은 사용자 입력이므로 알 수 없는 값은 검증 오류로 다룬다. */
    private static AiProvider parseSelection(String value) {
        return Arrays.stream(AiProvider.values())
                .filter(p -> p.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("provider", "ai.selection.noKey"));
    }
}
