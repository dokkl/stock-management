package com.stockmanagement.portfolio;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** quickstart S2: 계좌·보유 종목 등록 (US1). */
class RegisterHoldingsE2E extends E2ETestBase {

    @BeforeEach
    void seedSecurities() {
        Fixtures f = new Fixtures(jdbc);
        f.samsung();
        f.apple();
    }

    private Locator account(String name) {
        return page.locator("section.account").filter(new Locator.FilterOptions().setHasText(name));
    }

    private void createAccount(String name) {
        page.getByLabel("새 계좌 이름").fill(name);
        page.waitForResponse(
                r -> r.url().endsWith("/accounts"),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("계좌 추가"))
                        .click());
        assertThat(account(name)).isVisible();
        waitForHtmxIdle();
    }

    private void addHolding(String accountName, String query, String optionText, String avgPrice, String qty) {
        Locator acc = account(accountName);
        waitForHtmxIdle();
        acc.getByLabel("종목 검색").fill(query);
        acc.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(optionText))
                .click();
        acc.getByLabel("평단가").fill(avgPrice);
        acc.getByLabel("수량").fill(qty);
        page.waitForResponse(
                r -> r.url().contains("/holdings"),
                () -> acc.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("종목 추가"))
                        .click());
    }

    @Test
    void registersKoreanAndUsHoldings() {
        setupAndLogin();
        assertThat(page.getByText("계좌를 추가해 보세요")).isVisible();
        assertNoAccessibilityViolations();

        createAccount("키움 일반");
        addHolding("키움 일반", "삼성", "005930", "71500", "10");
        Locator samsungRow =
                account("키움 일반").getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("삼성전자"));
        assertThat(samsungRow).containsText("₩71,500");
        assertThat(samsungRow).containsText("10주");

        createAccount("미래에셋 해외");
        addHolding("미래에셋 해외", "AAPL", "AAPL", "185.20", "0.35");
        Locator appleRow =
                account("미래에셋 해외").getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("AAPL"));
        assertThat(appleRow).containsText("$185.2000");
        assertThat(appleRow).containsText("0.35주");

        assertNoAccessibilityViolations();
    }

    @Test
    void showsValidationAndDuplicateErrors() {
        setupAndLogin();
        createAccount("키움 일반");
        addHolding("키움 일반", "삼성", "005930", "71500", "10");

        addHolding("키움 일반", "삼성", "005930", "70000", "1");
        assertThat(account("키움 일반").getByRole(AriaRole.ALERT)).containsText("이미 등록된 종목입니다");

        addHolding("키움 일반", "AAPL", "AAPL", "0", "1");
        assertThat(account("키움 일반").getByRole(AriaRole.ALERT)).containsText("평단가는 0보다 커야 합니다");
        assertNoAccessibilityViolations();
    }

    @Test
    void deletesHoldingAndAccountWithConfirmation() {
        setupAndLogin();
        createAccount("키움 일반");
        addHolding("키움 일반", "삼성", "005930", "71500", "10");

        account("키움 일반")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("삼성전자 삭제"))
                .click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        assertThat(dialog).isVisible();
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("확인"))
                .click();
        assertThat(account("키움 일반").getByText("삼성전자")).hasCount(0);

        account("키움 일반")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("계좌 삭제"))
                .click();
        assertThat(dialog).isVisible();
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("확인"))
                .click();
        assertThat(account("키움 일반")).hasCount(0);
    }
}
