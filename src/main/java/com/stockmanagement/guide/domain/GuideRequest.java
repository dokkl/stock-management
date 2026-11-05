package com.stockmanagement.guide.domain;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.KstDate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 가이드 요청 1건과 상태 전이(data-model.md guide_request). PENDING에서만 SUCCEEDED 또는 FAILED로 바뀌고,
 * 종료 상태는 다시 바꿀 수 없다(FR-028).
 */
public record GuideRequest(
        Long id,
        long securityId,
        LocalDate usageDate,
        int slot,
        GuideStatus status,
        FailureKind failureKind,
        AiProvider provider,
        String model,
        GuideContent content,
        String rawText,
        Instant requestedAt,
        Instant completedAt) {

    public GuideRequest {
        Objects.requireNonNull(usageDate, "usageDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (slot < 1 || slot > GuideQuota.DAILY_LIMIT) {
            throw new IllegalArgumentException("slot은 1 또는 2입니다: " + slot);
        }
    }

    public static GuideRequest pending(Long id, long securityId, Instant requestedAt, int slot, AiProvider provider) {
        return new GuideRequest(
                id,
                securityId,
                usageDateOf(requestedAt),
                slot,
                GuideStatus.PENDING,
                null,
                provider,
                null,
                null,
                null,
                requestedAt,
                null);
    }

    /** 한도 집계 날짜: 요청을 시작한 시각의 KST 날짜(FR-018). */
    public static LocalDate usageDateOf(Instant requestedAt) {
        return LocalDate.ofInstant(requestedAt, KstDate.ZONE);
    }

    /** 구조화 결과 또는(파싱 실패 시) 원문 중 하나는 있어야 한다. */
    public GuideRequest succeed(String model, GuideContent content, String rawText, Instant at) {
        requirePending();
        if (content == null && (rawText == null || rawText.isBlank())) {
            throw new IllegalArgumentException("가이드 결과가 비어 있습니다");
        }
        return new GuideRequest(
                id,
                securityId,
                usageDate,
                slot,
                GuideStatus.SUCCEEDED,
                null,
                provider,
                model,
                content,
                rawText,
                requestedAt,
                at);
    }

    public GuideRequest fail(FailureKind kind, Instant at) {
        requirePending();
        Objects.requireNonNull(kind, "kind");
        return new GuideRequest(
                id,
                securityId,
                usageDate,
                slot,
                GuideStatus.FAILED,
                kind,
                provider,
                model,
                null,
                null,
                requestedAt,
                at);
    }

    public boolean isTerminal() {
        return status != GuideStatus.PENDING;
    }

    public boolean isStructured() {
        return content != null;
    }

    private void requirePending() {
        if (isTerminal()) {
            throw new IllegalStateException("종료된 가이드 요청은 바꿀 수 없습니다: id=" + id + ", status=" + status);
        }
    }
}
