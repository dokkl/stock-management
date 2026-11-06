package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.domain.AiProvider;

/** 공급자가 키를 거부했다(FR-023a). 저장하지 않는다. */
public class CredentialRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AiProvider provider;

    public CredentialRejectedException(AiProvider provider) {
        super("AI 키 검증 실패: provider=" + provider);
        this.provider = provider;
    }

    public AiProvider getProvider() {
        return provider;
    }
}
