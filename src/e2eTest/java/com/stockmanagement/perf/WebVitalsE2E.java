package com.stockmanagement.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 헌법 원칙 III·IV 프런트엔드 예산(T120): 기준 데이터셋(계좌 2개, 보유 종목 20건) 대시보드에서 LCP ≤ 2.5s,
 * CLS ≤ 0.1, INP ≤ 200ms, 초기 JS(gzip) ≤ 200KB, 버튼 클릭 후 100ms 이내 aria-busy.
 */
class WebVitalsE2E extends E2ETestBase {

    /** 페이지 로드 전에 붙여 LCP·CLS·이벤트 지연(INP 근사)을 모은다. */
    private static final String OBSERVERS = """
            window.__vitals = { lcp: 0, cls: 0, inp: 0 };
            new PerformanceObserver(list => {
              for (const e of list.getEntries()) { window.__vitals.lcp = Math.max(window.__vitals.lcp, e.startTime); }
            }).observe({ type: 'largest-contentful-paint', buffered: true });
            new PerformanceObserver(list => {
              for (const e of list.getEntries()) { if (!e.hadRecentInput) { window.__vitals.cls += e.value; } }
            }).observe({ type: 'layout-shift', buffered: true });
            new PerformanceObserver(list => {
              for (const e of list.getEntries()) {
                if (e.interactionId) { window.__vitals.inp = Math.max(window.__vitals.inp, e.duration); }
              }
            }).observe({ type: 'event', durationThreshold: 16, buffered: true });
            """;

    @BeforeEach
    void seedBaselineDataset() {
        Fixtures f = new Fixtures(jdbc);
        long first = f.account("기준 계좌 1");
        f.account("기준 계좌 2");
        f.manyHoldings(first, 20);
    }

    private double vital(String name) {
        return ((Number) page.evaluate("() => window.__vitals." + name)).doubleValue();
    }

    @Test
    void dashboardMeetsFrontendBudgets() {
        setupAndLogin();
        page.addInitScript(OBSERVERS);
        page.navigate(url("/"));
        waitForHtmx();
        page.waitForLoadState();
        assertThat(page.locator("tr[id^=holding-]").count()).isEqualTo(20);

        assertThat(vital("lcp")).as("LCP(ms)").isPositive().isLessThanOrEqualTo(2500);
        assertThat(vital("cls")).as("CLS").isLessThanOrEqualTo(0.1);

        // 상호작용: 보유 종목 수정 폼 열기 → 저장
        Locator row = page.locator("tr[id^=holding-]").first();
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(java.util.regex.Pattern.compile(".* 수정")))
                .click();
        waitForHtmxIdle();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("저장"))
                .first()
                .click();
        waitForHtmxIdle();
        page.waitForTimeout(300);
        assertThat(vital("inp")).as("INP 근사(ms)").isLessThanOrEqualTo(200);
    }

    @Test
    void initialJavaScriptIsUnderTwoHundredKilobytesGzipped() {
        setupAndLogin();
        @SuppressWarnings("unchecked")
        List<String> scripts =
                (List<String>) page.evaluate("() => [...document.querySelectorAll('script[src]')].map(s => s.src)");
        long gzipped = 0;
        for (String src : scripts) {
            APIResponse response = page.request().get(src);
            assertThat(response.ok()).as(src).isTrue();
            gzipped += gzipSize(response.body());
        }
        assertThat(scripts).isNotEmpty();
        assertThat(gzipped).as("초기 JS gzip 바이트").isLessThanOrEqualTo(200 * 1024);
    }

    @Test
    void buttonShowsBusyWithinHundredMilliseconds() {
        setupAndLogin();
        waitForHtmxIdle();

        Number elapsed = (Number) page.evaluate("""
                () => new Promise(resolve => {
                  const button = document.querySelector('tr[id^=holding-] button[hx-get$="/edit"]');
                  const start = performance.now();
                  new MutationObserver((records, observer) => {
                    if (button.getAttribute('aria-busy') === 'true') {
                      observer.disconnect();
                      resolve(performance.now() - start);
                    }
                  }).observe(button, { attributes: true });
                  button.click();
                  setTimeout(() => resolve(9999), 2000);
                })
                """);

        assertThat(elapsed.doubleValue()).as("aria-busy까지 걸린 시간(ms)").isLessThanOrEqualTo(100);
    }

    private static int gzipSize(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.size();
    }
}
