package com.stockmanagement.aicredential.application.port;

import com.stockmanagement.aicredential.application.EncryptedApiKey;
import com.stockmanagement.aicredential.application.StoredCredential;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.crypto.EncryptedSecret;
import com.stockmanagement.user.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 사용자별 AI 키 저장소(ai_credential)와 공급자 선택(app_user.selected_ai_provider). */
public interface CredentialStore {

    /** 등록된 키 목록(공급자 순). 암호문은 포함하지 않는다. */
    List<StoredCredential> findAll(UserId userId);

    /** 공급자 키 암호문. 가이드 실행 직전에만 복호화한다. */
    Optional<EncryptedSecret> findSecret(UserId userId, AiProvider provider);

    /** 공급자 키를 저장한다. 이미 있으면 교체한다. */
    void save(UserId userId, AiProvider provider, EncryptedApiKey key, Instant verifiedAt);

    /** 공급자 키를 지운다. 없었으면 false. */
    boolean delete(UserId userId, AiProvider provider);

    Optional<AiProvider> selectedProvider(UserId userId);

    /** 가이드용 공급자를 정한다. null이면 선택을 비운다. */
    void select(UserId userId, AiProvider provider);
}
