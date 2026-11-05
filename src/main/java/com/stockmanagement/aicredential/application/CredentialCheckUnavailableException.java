package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.domain.AiProvider;

/** 일시 장애로 키를 확인하지 못했다(FR-023e). 저장하지 않으며, 잠시 후 다시 시도할 수 있다. */
public class CredentialCheckUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AiProvider provider;

    public CredentialCheckUnavailableException(AiProvider provider) {
        super("AI 키 검증 일시 장애: provider=" + provider);
        this.provider = provider;
    }

    public AiProvider getProvider() {
        return provider;
    }
}
