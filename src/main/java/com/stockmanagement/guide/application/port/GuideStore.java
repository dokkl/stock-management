package com.stockmanagement.guide.application.port;

import com.stockmanagement.guide.application.GuideJob;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.GuideTarget;
import com.stockmanagement.guide.application.GuideView;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.user.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;

/** 가이드 요청 저장소(guide_request). 사용자별 하루 한도는 DB 부분 유니크 인덱스가 보장한다(research R7, 002 R5). */
public interface GuideStore {

    /** 보유 종목과 현재 종목 상태·시세. 삭제된 계좌의 보유 종목은 없다고 본다. */
    Optional<GuideTarget> findTarget(UserId userId, long holdingId);

    /** 이 종목·날짜에 진행 중인 요청. */
    OptionalLong findPending(UserId userId, long securityId, LocalDate usageDate);

    /** 슬롯 1, 2 순서로 예약한다. 둘 다 차 있으면 비어 있다(한도 초과). */
    OptionalLong reserve(GuideReservation reservation);

    /** 이 종목·날짜의 실패하지 않은 요청 수. */
    int countActive(UserId userId, long securityId, LocalDate usageDate);

    /** 백그라운드 실행용(사용자 범위 없음). 요청한 사용자는 GuideJob.userId에 있다. */
    Optional<GuideJob> findJob(long id);

    Optional<GuideView> find(UserId userId, long id);

    /** PENDING일 때만 성공으로 바꾼다. 바꿨으면 true. */
    boolean markSucceeded(long id, String model, GuideContent content, String rawText, Instant completedAt);

    /** PENDING일 때만 실패로 바꾼다(슬롯 반환). 바꿨으면 true. */
    boolean markFailed(long id, FailureKind kind, Instant completedAt);

    /** requestedBefore 이전에 시작해 아직 PENDING인 요청을 일시 장애 실패로 바꾼다. 바꾼 건수. */
    int failStale(Instant requestedBefore, Instant completedAt);
}
