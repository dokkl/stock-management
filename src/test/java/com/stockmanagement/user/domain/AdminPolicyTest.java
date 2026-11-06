package com.stockmanagement.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdminPolicyTest {

    @Test
    void listedKakaoIdsAreAdmins() {
        AdminPolicy policy = AdminPolicy.of(List.of(9001L, 9002L));
        assertThat(policy.isAdmin(new KakaoId(9001))).isTrue();
        assertThat(policy.isAdmin(new KakaoId(9002))).isTrue();
        assertThat(policy.isAdmin(new KakaoId(1001))).isFalse();
    }

    @Test
    void noAdminsWhenUnset() {
        assertThat(AdminPolicy.of(null).isAdmin(new KakaoId(1))).isFalse();
        assertThat(AdminPolicy.of(List.of()).isAdmin(new KakaoId(1))).isFalse();
    }
}
