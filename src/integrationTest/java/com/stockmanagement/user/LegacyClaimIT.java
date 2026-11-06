package com.stockmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.application.UserService;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 기존 데이터 인수(T018, FR-017, research R6): 첫 카카오 로그인이 레거시 사용자를 차지하고, 동시에 와도 한 명만. */
class LegacyClaimIT extends IntegrationTestBase {

    @Autowired
    private UserService userService;

    private long createLegacyUserWithAccount() {
        long legacy = jdbc.sql(
                        "INSERT INTO app_user (display_name, legacy, created_at) VALUES ('기존 사용자', true, now()) RETURNING id")
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO account (user_id, name, created_at, updated_at) VALUES (:u, '키움 일반', now(), now())")
                .param("u", legacy)
                .update();
        return legacy;
    }

    @Test
    void firstKakaoLoginClaimsLegacyDataAndNextUserStartsEmpty() {
        long legacy = createLegacyUserWithAccount();

        UserId first = userService.signIn(new KakaoId(2001), DisplayName.of("운영자"));
        UserId second = userService.signIn(new KakaoId(2002), DisplayName.of("친구"));

        assertThat(first.value()).isEqualTo(legacy);
        assertThat(second.value()).isNotEqualTo(legacy);
        assertThat(jdbc.sql("SELECT legacy, kakao_id FROM app_user WHERE id = :id")
                        .param("id", legacy)
                        .query()
                        .singleRow())
                .containsEntry("legacy", false)
                .containsEntry("kakao_id", 2001L);
        assertThat(jdbc.sql("SELECT count(*) FROM account WHERE user_id = :u")
                        .param("u", second.value())
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(userService.signIn(new KakaoId(2001), DisplayName.of("운영자")))
                .as("재로그인")
                .isEqualTo(first);
    }

    @Test
    void concurrentFirstLoginsClaimLegacyOnlyOnce() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 50; round++) {
                long legacy = createLegacyUserWithAccount();
                CountDownLatch start = new CountDownLatch(1);
                long base = 10_000L + round * 10L;
                List<Future<UserId>> results = List.of(
                        executor.submit(() -> {
                            start.await();
                            return userService.signIn(new KakaoId(base + 1), DisplayName.of("A"));
                        }),
                        executor.submit(() -> {
                            start.await();
                            return userService.signIn(new KakaoId(base + 2), DisplayName.of("B"));
                        }));
                start.countDown();
                long claimed = 0;
                for (Future<UserId> result : results) {
                    claimed += result.get().value() == legacy ? 1 : 0;
                }
                assertThat(claimed).as("round %d", round).isEqualTo(1);
                jdbc.sql("DELETE FROM app_user").update();
            }
        }
    }
}
