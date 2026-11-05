package com.stockmanagement.auth;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import org.junit.jupiter.api.Test;

/** quickstart S1: 최초 설정, 잠금, 재설정 불가. */
class AuthE2E extends E2ETestBase {

    @Test
    void firstVisitGoesToSetupAndScreensAreAccessible() {
        page.navigate(url("/"));
        assertThat(page).hasURL(url("/setup"));
        assertNoAccessibilityViolations();

        page.getByLabel("비밀번호", new Page.GetByLabelOptions().setExact(true)).fill(OWNER_PASSWORD);
        page.getByLabel("비밀번호 확인").fill(OWNER_PASSWORD);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("설정 완료"))
                .click();

        assertThat(page).hasURL(url("/login"));
        assertNoAccessibilityViolations();
    }

    @Test
    void fiveWrongPasswordsLockLogin() {
        setupAndLogin();
        context.clearCookies();
        for (int i = 0; i < 5; i++) {
            page.navigate(url("/login"));
            page.getByLabel("비밀번호").fill("wrong-password-xx");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("로그인"))
                    .click();
        }
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("15분");
        assertNoAccessibilityViolations();
    }

    @Test
    void setupIsNotAvailableAfterCompletion() {
        setupAndLogin();
        Response response = page.navigate(url("/setup"));
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(404);
    }
}
