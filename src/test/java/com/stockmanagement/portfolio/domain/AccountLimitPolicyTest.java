package com.stockmanagement.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountLimitPolicyTest {

    private final AccountLimitPolicy policy = new AccountLimitPolicy(2);

    @Test
    void allowsUpToLimit() {
        assertThatCode(() -> policy.checkCanAdd(0)).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkCanAdd(1)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAtLimitWithMessageKeyAndMax() {
        assertThatThrownBy(() -> policy.checkCanAdd(2))
                .isInstanceOfSatisfying(AccountLimitExceededException.class, e -> {
                    assertThat(e.getMaxAccounts()).isEqualTo(2);
                    assertThat(e.getField()).isEqualTo("name");
                    assertThat(e.getMessageKey()).isEqualTo("account.limit");
                });
    }

    @Test
    void limitMustBePositive() {
        assertThatThrownBy(() -> new AccountLimitPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
