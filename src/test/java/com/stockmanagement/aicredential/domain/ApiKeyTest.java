package com.stockmanagement.aicredential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

class ApiKeyTest {

    private static final String RAW = "sk-ant-api03-secret-value-1234";

    @Test
    void toStringShowsOnlyLastFour() {
        ApiKey key = ApiKey.of(RAW);
        assertThat(key).hasToString("****1234");
        assertThat(key.last4()).isEqualTo("1234");
        assertThat(key.reveal()).isEqualTo(RAW);
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(ApiKey.of("  " + RAW + "\n").reveal()).isEqualTo(RAW);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> ApiKey.of(null))
                .isInstanceOfSatisfying(
                        DomainValidationException.class,
                        e -> assertThat(e.getMessageKey()).isEqualTo("ai.key.required"));
        assertThatThrownBy(() -> ApiKey.of("   ")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsTooShortTooLongOrInnerWhitespace() {
        assertThatThrownBy(() -> ApiKey.of("short")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ApiKey.of("x".repeat(513))).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ApiKey.of("sk-ant abc def ghi")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void equalityComparesValueWithoutExposingIt() {
        ApiKey a = ApiKey.of(RAW);
        assertThat(a).isEqualTo(ApiKey.of(RAW)).hasSameHashCodeAs(ApiKey.of(RAW));
        assertThat(a).isNotEqualTo(ApiKey.of("sk-other-secret-5678")).isNotEqualTo(RAW);
        assertThat(String.valueOf(a)).doesNotContain("secret");
    }

    @Test
    void providerHasDisplayName() {
        assertThat(AiProvider.ANTHROPIC.displayName()).isEqualTo("Claude");
        assertThat(AiProvider.OPENAI.displayName()).isEqualTo("OpenAI");
    }
}
