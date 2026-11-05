package com.stockmanagement.guide;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** quickstart S6: 가이드 기록 조회(US5) — 최신순, 당시 스냅샷 유지, 보유 종목 삭제 후 "보유 종료". */
class GuideHistoryE2E extends E2ETestBase {

    private long samsung;
    private long holding;

    @BeforeEach
    void seed() {
        Fixtures f = new Fixtures(jdbc);
        long account = f.account("키움 일반");
        samsung = f.samsung();
        holding = f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", Instant.now());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        guide(account, now.minus(2, ChronoUnit.DAYS), "오래된 판단입니다.", "HOLD");
        guide(account, now.minus(1, ChronoUnit.DAYS), "어제의 판단입니다.", "WATCH");
        guide(account, now, "오늘의 판단입니다.", "BUY_MORE");
    }

    private void guide(long account, Instant at, String overall, String action) {
        String content =
                "{\"summary\":\"요약\",\"diagnosis\":\"진단\",\"supplyDemand\":\"수급\",\"issues\":[]," + "\"overall\":\""
                        + overall + "\",\"action\":\"" + action + "\",\"rationale\":\"근거\",\"risks\":[\"위험\"]}";
        jdbc.sql("""
                        INSERT INTO guide_request (security_id, holding_id, account_id, usage_date, slot, status, provider,
                            model, account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot,
                            return_rate_snapshot, currency, content, requested_at, completed_at)
                        VALUES (:s, :h, :a, CAST(:at AS date), 1, 'SUCCEEDED', 'ANTHROPIC', 'claude-sonnet-5-5', '키움 일반',
                            71000, 71500, 10, -0.0070, 'KRW', CAST(:content AS jsonb), :at, :at)
                        """)
                .param("s", samsung)
                .param("h", holding)
                .param("a", account)
                .param("at", Timestamp.from(at))
                .param("content", content)
                .update();
    }

    private Locator entries() {
        return page.locator(".guide-history > li");
    }

    @Test
    void showsHistoryNewestFirstAndKeepsSnapshot() {
        setupAndLogin();

        // S6-1: 대시보드의 "기록" 링크 → 최신순 기록과 당시 값
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("삼성전자 가이드 기록 보기"))
                .click();
        page.waitForURL(url("/securities/" + samsung + "/guides"));
        assertThat(entries()).hasCount(3);
        assertThat(entries().nth(0)).containsText("오늘의 판단입니다.");
        assertThat(entries().nth(0)).containsText("추가 매수");
        assertThat(entries().nth(2)).containsText("오래된 판단입니다.");
        assertThat(entries().nth(0)).containsText("₩71,500");
        assertThat(entries().nth(0)).containsText("−0.70%");
        assertNoAccessibilityViolations();

        // S6-2: 평단가를 바꿔도 기록은 당시 값
        jdbc.sql("UPDATE holding SET avg_price = 60000 WHERE id = :id")
                .param("id", holding)
                .update();
        page.reload();
        assertThat(entries().nth(0)).containsText("₩71,500");

        // S6-3: 보유 종목을 삭제해도 기록은 남고 "보유 종료"
        page.navigate(url("/"));
        waitForHtmx();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("삼성전자 삭제"))
                .click();
        page.locator("#confirm-dialog")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("확인"))
                .click();
        assertThat(page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("삼성전자")))
                .hasCount(0);
        page.navigate(url("/securities/" + samsung + "/guides"));
        assertThat(entries()).hasCount(3);
        assertThat(entries().nth(0)).containsText("보유 종료");
        assertNoAccessibilityViolations();

        // 기록 상세로 이동
        entries().nth(0).getByRole(AriaRole.LINK).first().click();
        assertThat(page.getByText("오늘의 판단입니다.")).isVisible();
    }
}
