package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import java.util.List;

/** AI 설정 화면: 공급자별 키 상태와 선택된 공급자. */
public record AiSettingsView(List<ProviderStatus> providers, AiProvider selected) {

    public boolean hasAnyKey() {
        return providers.stream().anyMatch(ProviderStatus::hasKey);
    }

    /**
     * 공급자 1개의 키 상태.
     *
     * @param last4 등록된 키의 끝 4자리. 키가 없으면 null
     */
    public record ProviderStatus(AiProvider provider, String last4) {

        public boolean hasKey() {
            return last4 != null;
        }

        public String maskedKey() {
            return hasKey() ? "****" + last4 : null;
        }
    }
}
