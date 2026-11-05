package com.stockmanagement.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class KakaoIdTest {

    @Test
    void onlyPositive() {
        assertThat(new KakaoId(1234567890L)).hasToString("1234567890");
        assertThatThrownBy(() -> new KakaoId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KakaoId(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userIdIsPositiveValue() {
        assertThat(new UserId(7)).isEqualTo(new UserId(7)).hasToString("7");
        assertThatThrownBy(() -> new UserId(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appUserRequiresIdAndName() {
        AppUser user = new AppUser(new UserId(1), null, DisplayName.of("기존 사용자"), Instant.EPOCH);
        assertThat(user.kakaoId()).isNull();
        assertThatThrownBy(() -> new AppUser(null, null, DisplayName.of("x"), Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
    }
}
