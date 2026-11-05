package com.stockmanagement.user;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.KakaoStubs;
import org.junit.jupiter.api.Test;

/** quickstart K4: 회원 탈퇴(US4). */
class WithdrawalE2E extends E2ETestBase {

    @Test
    void withdrawThenReloginStartsFresh() {
        Fixtures f = new Fixtures(jdbc);
        f.account(f.user(14001, "탈퇴할 사용자"), "지울 계좌");
        loginAs(14001, "탈퇴할 사용자");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("지울 계좌")))
                .isVisible();
        new KakaoStubs(WIREMOCK).unlinkOk();

        page.locator(".nav-user").click();
        page.waitForURL(url("/settings/account"));
        waitForHtmx();
        assertNoAccessibilityViolations();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("탈퇴하기"))
                .click();
        page.locator("#confirm-dialog")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("확인"))
                .click();

        page.waitForURL(url("/login?withdrawn"));
        assertThat(page.getByText("탈퇴가 완료되었습니다")).isVisible();

        loginAs(14001, "탈퇴할 사용자");
        assertThat(page.getByText("계좌를 추가해 보세요")).isVisible();
        assertThat(page.getByText("지울 계좌")).hasCount(0);
    }
}
