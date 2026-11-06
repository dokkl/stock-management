package com.stockmanagement.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.portfolio.application.AccountService;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.domain.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 사용자별 한도(T033, FR-013): 계좌 2·보유 20은 사용자마다 따로 센다. */
class PerUserLimitsIT extends IntegrationTestBase {

    @Autowired
    private AccountService accountService;

    @Test
    void accountLimitIsPerUser() throws Exception {
        UserId a = userWithKakao(11001);
        UserId b = userWithKakao(11002);
        new Fixtures(jdbc).account(a.value(), "A1");
        new Fixtures(jdbc).account(a.value(), "A2");

        mockMvc.perform(post("/accounts").param("name", "A3").with(as(a)).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("계좌는 최대 2개")));
        mockMvc.perform(post("/accounts").param("name", "B1").with(as(b)).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void holdingLimitIsPerUser() throws Exception {
        Fixtures f = new Fixtures(jdbc);
        UserId a = userWithKakao(11003);
        UserId b = userWithKakao(11004);
        f.manyHoldings(f.account(a.value(), "A 계좌"), 20);
        long apple = f.apple();
        long bAccount = f.account(b.value(), "B 계좌");

        mockMvc.perform(post("/accounts/" + f.account(a.value(), "A 두번째") + "/holdings")
                        .param("securityId", String.valueOf(apple))
                        .param("avgPrice", "185")
                        .param("quantity", "1")
                        .with(as(a))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("최대 20개")));
        mockMvc.perform(post("/accounts/" + bAccount + "/holdings")
                        .param("securityId", String.valueOf(apple))
                        .param("avgPrice", "185")
                        .param("quantity", "1")
                        .with(as(b))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentAccountCreationNeverExceedsLimit() throws Exception {
        UserId a = userWithKakao(11005);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 20; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> results = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    String name = "계좌-" + round + "-" + i;
                    results.add(executor.submit(() -> {
                        start.await();
                        try {
                            accountService.create(a, name);
                        } catch (RuntimeException ignored) {
                            // 한도 초과는 예상된 결과다
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> result : results) {
                    result.get();
                }
                assertThat(jdbc.sql("SELECT count(*) FROM account WHERE user_id = :u AND deleted_at IS NULL")
                                .param("u", a.value())
                                .query(Long.class)
                                .single())
                        .as("round %d", round)
                        .isEqualTo(2);
                jdbc.sql("DELETE FROM account").update();
            }
        }
    }
}
