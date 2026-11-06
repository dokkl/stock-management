package com.stockmanagement.user;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import org.junit.jupiter.api.Test;

/** quickstart K2: 두 사용자의 데이터 분리(US2). */
class DataIsolationE2E extends E2ETestBase {

    @Test
    void usersSeeOnlyTheirOwnData() {
        Fixtures f = new Fixtures(jdbc);
        long userA = f.user(9001, "사용자A");
        long aHolding = f.holding(f.account(userA, "A의 키움"), f.samsung(), "71500", "10");

        loginAs(9001, "사용자A");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("A의 키움")))
                .isVisible();

        Page b = openSecondBrowser();
        loginAs(b, 9002, "사용자B");
        assertThat(b.getByText("계좌를 추가해 보세요")).isVisible();
        assertThat(b.getByText("A의 키움")).hasCount(0);
        assertThat(b.locator(".nav-user")).hasText("사용자B");

        b.navigate(url("/holdings/" + aHolding + "/edit"));
        assertThat(b.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("페이지를 찾을 수 없습니다")))
                .isVisible();

        page.reload();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("A의 키움")))
                .isVisible();
        assertNoAccessibilityViolations();
    }
}
