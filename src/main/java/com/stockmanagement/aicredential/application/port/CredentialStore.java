package com.stockmanagement.aicredential.application.port;

import com.stockmanagement.aicredential.application.EncryptedApiKey;
import com.stockmanagement.aicredential.application.StoredCredential;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.crypto.EncryptedSecret;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 공용 AI 키 저장소(ai_credential)와 가이드용 공급자 선택(ai_settings). 관리자가 등록한 키를 모든 사용자가 쓴다. 사용자 범위가
 * 없는 공유 데이터다(관리자만 바꾸는 것은 웹 계층의 접근 제어가 보장한다).
 */
public interface CredentialStore {

    /** 등록된 키 목록(공급자 순). 암호문은 포함하지 않는다. */
    List<StoredCredential> findAll();

    /** 공급자 키 암호문. 가이드 실행 직전에만 복호화한다. */
    Optional<EncryptedSecret> findSecret(AiProvider provider);

    /** 공급자 키를 저장한다. 이미 있으면 교체한다. */
    void save(AiProvider provider, EncryptedApiKey key, Instant verifiedAt);

    /** 공급자 키를 지운다. 없었으면 false. */
    boolean delete(AiProvider provider);

    Optional<AiProvider> selectedProvider();

    /** 가이드용 공급자를 정한다. null이면 선택을 비운다. */
    void select(AiProvider provider);
}
