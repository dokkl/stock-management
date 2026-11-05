package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuideOutcomeTest {

    private static final GuideContent CONTENT = new GuideContent("요약", "진단", GuideAction.WATCH, "근거", List.of("위험"));

    @Test
    void generatedNeedsContentOrRawText() {
        assertThat(new GuideOutcome.Generated("m", CONTENT, null).content()).isEqualTo(CONTENT);
        assertThat(new GuideOutcome.Generated("m", null, "원문").rawText()).isEqualTo("원문");
        assertThatThrownBy(() -> new GuideOutcome.Generated("m", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideOutcome.Generated("m", null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideOutcome.Generated(null, CONTENT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void errorsCarryStatus() {
        assertThat(new GuideOutcome.CredentialError(401).status()).isEqualTo(401);
        assertThat(new GuideOutcome.TransientError(0).status()).isZero();
    }
}
