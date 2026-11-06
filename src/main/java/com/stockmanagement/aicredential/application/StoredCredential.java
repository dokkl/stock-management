package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import java.time.Instant;

/** 등록된 키의 표시 정보(원문·암호문 없음). */
public record StoredCredential(AiProvider provider, String last4, Instant verifiedAt) {}
