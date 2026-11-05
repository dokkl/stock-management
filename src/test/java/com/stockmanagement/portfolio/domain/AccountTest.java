package com.stockmanagement.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void trimsName() {
        assertThat(AccountName.of("  키움 일반  ").value()).isEqualTo("키움 일반");
    }

    @Test
    void acceptsOneAndThirtyCharacters() {
        assertThat(AccountName.of("a").value()).isEqualTo("a");
        assertThat(AccountName.of("가".repeat(30)).value()).hasSize(30);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> AccountName.of("   "))
                .isInstanceOf(DomainValidationException.class)
                .extracting("messageKey")
                .isEqualTo("account.name.required");
        assertThatThrownBy(() -> AccountName.of(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsThirtyOneCharacters() {
        assertThatThrownBy(() -> AccountName.of("가".repeat(31)))
                .isInstanceOf(DomainValidationException.class)
                .extracting("messageKey")
                .isEqualTo("account.name.tooLong");
    }
}
