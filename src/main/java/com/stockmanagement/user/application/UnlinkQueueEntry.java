package com.stockmanagement.user.application;

import com.stockmanagement.user.domain.KakaoId;

/** 연결 해제 재시도 대기열 항목. */
public record UnlinkQueueEntry(KakaoId kakaoId, int attempts) {}
