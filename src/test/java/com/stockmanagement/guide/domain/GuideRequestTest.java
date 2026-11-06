package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.aicredential.domain.AiProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideRequestTest {

    private static final Instant REQUESTED = Instant.parse("2026-11-05T01:00:00Z");
    private static final Instant DONE = REQUESTED.plusSeconds(20);
    private static final GuideContent CONTENT = new GuideContent("요약", "진단", GuideAction.HOLD, "근거", List.of("위험"));

    private static GuideRequest pending() {
        return GuideRequest.pending(7L, 3L, REQUESTED, 1, AiProvider.OPENAI);
    }

    @Test
    void pendingCanSucceedWithContent() {
        GuideRequest done = pending().succeed("gpt-5", CONTENT, null, DONE);

        assertThat(done.status()).isEqualTo(GuideStatus.SUCCEEDED);
        assertThat(done.model()).isEqualTo("gpt-5");
        assertThat(done.content()).isEqualTo(CONTENT);
        assertThat(done.completedAt()).isEqualTo(DONE);
        assertThat(done.isTerminal()).isTrue();
    }

    @Test
    void pendingCanSucceedWithRawTextFallback() {
        GuideRequest done = pending().succeed("gpt-5", null, "원문", DONE);
        assertThat(done.rawText()).isEqualTo("원문");
        assertThat(done.isStructured()).isFalse();
    }

    @Test
    void successNeedsContentOrRawText() {
        assertThatThrownBy(() -> pending().succeed("gpt-5", null, " ", DONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingCanFail() {
        GuideRequest failed = pending().fail(FailureKind.TRANSIENT_ERROR, DONE);
        assertThat(failed.status()).isEqualTo(GuideStatus.FAILED);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.TRANSIENT_ERROR);
        assertThat(failed.isTerminal()).isTrue();
    }

    @Test
    void terminalStatesCannotTransition() {
        GuideRequest succeeded = pending().succeed("gpt-5", CONTENT, null, DONE);
        GuideRequest failed = pending().fail(FailureKind.CREDENTIAL_ERROR, DONE);

        assertThatThrownBy(() -> succeeded.fail(FailureKind.TRANSIENT_ERROR, DONE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.succeed("gpt-5", CONTENT, null, DONE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> succeeded.succeed("gpt-5", CONTENT, null, DONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usageDateIsKstDateOfRequestStart() {
        assertThat(GuideRequest.usageDateOf(Instant.parse("2026-11-05T14:59:59.900Z")))
                .as("23:59:59.9 KST")
                .isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(GuideRequest.usageDateOf(Instant.parse("2026-11-05T15:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(pending().usageDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void slotMustBeOneOrTwo() {
        assertThatThrownBy(() -> GuideRequest.pending(1L, 1L, REQUESTED, 3, AiProvider.OPENAI))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuideRequest.pending(1L, 1L, REQUESTED, 0, AiProvider.OPENAI))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successWithoutAnyResultIsRejected() {
        assertThatThrownBy(() -> pending().succeed("gpt-5", null, null, DONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(pending().succeed("gpt-5", CONTENT, null, DONE).isStructured())
                .isTrue();
        assertThat(pending().isTerminal()).isFalse();
    }

    @Test
    void contentValidatesSchemaLimits() {
        assertThatThrownBy(() -> new GuideContent("", "진단", GuideAction.HOLD, "근거", List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent("요약", "진단", GuideAction.HOLD, "근거", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new GuideContent("요약", "진단", GuideAction.HOLD, "근거", List.of("1", "2", "3", "4", "5", "6")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent("요약", "진단", null, "근거", List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent("요약".repeat(1000), "진단", GuideAction.HOLD, "근거", List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent("요약", "진단", GuideAction.HOLD, "근거", List.of("가".repeat(301))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent(null, "진단", GuideAction.HOLD, "근거", List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideContent("요약", "진단", GuideAction.HOLD, "근거", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
