package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import java.time.Instant;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 가이드 서랍 패널(데스크톱 오른쪽, 모바일 하단 시트), 닫은 뒤 완료 토스트, 모바일 대시보드 카드 레이아웃. */
class GuideDrawerE2E extends E2ETestBase {

    private static final String GUIDE_JSON = "{\\\"summary\\\":\\\"완만한 상승입니다.\\\",\\\"diagnosis\\\":\\\"평단가 근처입니다.\\\","
            + "\\\"supplyDemand\\\":\\\"외국인 순매수입니다.\\\",\\\"issues\\\":[],\\\"overall\\\":\\\"서랍 패널 결론입니다.\\\","
            + "\\\"action\\\":\\\"HOLD\\\",\\\"rationale\\\":\\\"근거입니다.\\\",\\\"risks\\\":[\\\"위험\\\"]}";

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private CredentialCipher cipher;

    private long appleHolding;

    @BeforeEach
    void seed() {
        Fixtures f = new Fixtures(jdbc);
        long account = f.account("키움 일반");
        long samsung = f.samsung();
        f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", Instant.now());
        long apple = f.apple();
        appleHolding = f.holding(account, apple, "185.2", "0.35");
        f.quote(apple, "187.50", "190.00", "OPEN", Instant.now());
        credentialStore.save(
                defaultUserId(),
                AiProvider.OPENAI,
                cipher.encrypt(ApiKey.of("sk-proj-drawer-e2e-1234")),
                Instant.now());
        credentialStore.select(defaultUserId(), AiProvider.OPENAI);

        WIREMOCK.resetAll();
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .willReturn(json("{\"result\":{\"candles\":[{\"timestamp\":\"2026-11-04T00:00:00+09:00\","
                        + "\"closePrice\":\"70000\"}],\"nextBefore\":null}}")));
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(json("{\"id\":\"c1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-5\","
                                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                                + GUIDE_JSON
                                + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,"
                                + "\"total_tokens\":2}}")
                        .withFixedDelay(1_500)));
    }

    private Locator guideButton(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(name + " 가이드")));
    }

    private Locator drawer() {
        return page.locator("#guide-drawer");
    }

    @Test
    void drawerOpensClosesAndNotifiesWhenDoneInBackground() {
        setupAndLogin();

        guideButton("삼성전자").click();
        assertThat(drawer()).isVisible();
        assertThat(drawer().locator(".analysis-steps")).isVisible();
        assertNoAccessibilityViolations();

        // 닫으면 포커스가 버튼으로 돌아가고, 버튼은 진행 중 상태가 된다
        drawer().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("닫기"))
                .click();
        assertThat(drawer()).isHidden();
        assertThat(page.locator("#guide-btn-1 button")).isFocused();
        assertThat(page.locator("#guide-btn-1 button")).containsText("분석 중");

        // 완료되면 토스트 → "보기"로 결과가 있는 패널을 다시 연다
        Locator toast = page.locator(".toast-action");
        assertThat(toast).containsText("삼성전자", new LocatorAssertions.ContainsTextOptions().setTimeout(15_000));
        assertThat(toast).containsText("가이드가 준비되었습니다");
        toast.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("보기"))
                .click();
        assertThat(drawer()).isVisible();
        assertThat(drawer().locator(".guide-headline")).hasText("서랍 패널 결론입니다.");
        assertThat(page.locator("#guide-btn-1 button")).containsText("가이드 (남은 1회)");

        // Esc로 닫힌다
        page.keyboard().press("Escape");
        assertThat(drawer()).isHidden();
    }

    /** 오늘 한도(2회)를 다 쓴 것으로 만든다: 사유 문구("내일 00:00 (KST) 초기화")가 함께 표시된다. */
    private void exhaustGuideQuota(long holding) {
        for (int slot = 1; slot <= 2; slot++) {
            jdbc.sql("""
                            INSERT INTO guide_request (user_id, security_id, holding_id, account_id, usage_date, slot, status,
                                provider, model, account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot,
                                return_rate_snapshot, currency, content, requested_at, completed_at)
                            SELECT a.user_id, h.security_id, h.id, a.id, (now() AT TIME ZONE 'Asia/Seoul')::date, :slot,
                                'SUCCEEDED', 'OPENAI', 'gpt-5', a.name, 187.5, 185.2, 0.35, 0.0124, 'USD',
                                CAST(:content AS jsonb), now(), now()
                            FROM holding h JOIN account a ON a.id = h.account_id WHERE h.id = :h
                            """)
                    .param("slot", slot)
                    .param("h", holding)
                    .param("content", GUIDE_JSON.replace("\\\"", "\""))
                    .update();
        }
    }

    @Test
    void exhaustedQuotaReasonDoesNotPushRowActionsOutOfCard() {
        exhaustGuideQuota(appleHolding);
        setupAndLogin();
        Locator reason = page.locator("#guide-btn-" + appleHolding + " .guide-reason");
        assertThat(reason).hasText("내일 00:00 (KST) 초기화");

        Number tableRight =
                (Number) page.evaluate("() => document.querySelector('table.holdings').getBoundingClientRect().right");
        Number cardRight =
                (Number) page.evaluate("() => document.querySelector('section.account').getBoundingClientRect().right");
        assertThat(tableRight.doubleValue()).as("표가 계좌 카드 안에 있다").isLessThanOrEqualTo(cardRight.doubleValue());
        Number gap = (Number) page.evaluate("""
                id => { const span = document.getElementById(id);
                        const first = span.querySelector('button').getBoundingClientRect();
                        return first.left - span.getBoundingClientRect().left; }
                """, "guide-btn-" + appleHolding);
        assertThat(gap.intValue()).as("가이드 버튼 왼쪽에 빈 공간이 없다").isLessThanOrEqualTo(1);
    }

    @Test
    void mobileLayoutHasNoHorizontalOverflowAndBottomSheet() {
        page.setViewportSize(390, 844);
        setupAndLogin();

        Number overflow = (Number) page.evaluate("() => document.documentElement.scrollWidth - window.innerWidth");
        assertThat(overflow.intValue()).as("가로 스크롤 없음").isLessThanOrEqualTo(0);
        Locator row = page.locator("tr[id^=holding-]").first();
        assertThat(row.locator("td[data-label]").first()).isVisible();
        assertNoAccessibilityViolations();

        guideButton("삼성전자").click();
        assertThat(drawer()).isVisible();
        page.evaluate(
                "() => Promise.all(document.getElementById('guide-drawer').getAnimations().map(a => a.finished))");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Number> box = (java.util.Map<String, Number>) page.evaluate("""
                () => { const r = document.getElementById('guide-drawer').getBoundingClientRect();
                        return { left: r.left, width: r.width, bottom: r.bottom, top: r.top }; }
                """);
        assertThat(box.get("left").intValue()).isZero();
        assertThat(box.get("width").intValue()).isEqualTo(390);
        assertThat(box.get("bottom").intValue()).isEqualTo(844);
        assertThat(box.get("top").intValue()).as("하단 시트(위쪽에 대시보드가 조금 보임)").isPositive();
        assertThat(drawer().locator(".guide-headline"))
                .hasText("서랍 패널 결론입니다.", new LocatorAssertions.HasTextOptions().setTimeout(15_000));
        assertNoAccessibilityViolations();
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
