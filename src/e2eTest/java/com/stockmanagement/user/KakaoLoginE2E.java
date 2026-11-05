package com.stockmanagement.user;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.KakaoStubs;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** quickstart K1: 카카오로 가입·로그인·로그아웃·재로그인·취소(US1). */
class KakaoLoginE2E extends E2ETestBase {

    private Optional<String> sessionCookie() {
        return context.cookies().stream()
                .filter(c -> c.name.equals("JSESSIONID"))
                .map((Cookie c) -> c.value)
                .findFirst();
    }

    private void clickKakao() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("카카오로 시작하기"))
                .click();
    }

    @Test
    void firstLoginShowsEmptyDashboardAndReloginKeepsData() {
        page.navigate(url("/"));
        page.waitForURL(url("/login"));
        assertThat(page.locator("input[type=password]")).hasCount(0);
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("카카오로 시작하기")))
                .isVisible();
        assertNoAccessibilityViolations();
        String anonymousSession = sessionCookie().orElse("");

        loginAs(4001, "홍길동");

        assertThat(page.getByText("계좌를 추가해 보세요")).isVisible();
        assertThat(page.locator(".nav-user")).hasText("홍길동");
        org.assertj.core.api.Assertions.assertThat(sessionCookie())
                .as("로그인 후 세션 재발급")
                .isPresent()
                .get()
                .isNotEqualTo(anonymousSession);

        new Fixtures(jdbc).account(new Fixtures(jdbc).user(4001, "홍길동"), "키움 일반");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("로그아웃"))
                .click();
        page.waitForURL(url("/login?logout"));
        assertThat(page.getByText("로그아웃되었습니다")).isVisible();

        clickKakao();
        page.waitForURL(url("/"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("키움 일반")))
                .isVisible();
    }

    @Test
    void cancelledConsentShowsMessage() {
        new KakaoStubs(WIREMOCK).cancelAuthorize();
        page.navigate(url("/login"));

        clickKakao();

        page.waitForURL(url("/login?error=cancelled"));
        assertThat(page.getByText("카카오 로그인이 취소되었습니다")).isVisible();
        assertNoAccessibilityViolations();
    }
}
