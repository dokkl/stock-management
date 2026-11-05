package com.stockmanagement.aicredential.application;

import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 키 등록·삭제와 가이드용 공급자 선택(FR-023~FR-023c).
 *
 * <ul>
 *   <li>공급자가 키를 받아들였을 때만 암호화해 저장한다. 같은 공급자 키는 교체한다.
 *   <li>선택된 공급자가 없으면 방금 등록한 공급자를 선택한다.
 *   <li>선택된 공급자의 키를 지우면 남은 키의 공급자로 바꾸고, 없으면 선택을 비운다.
 * </ul>
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final CredentialStore store;
    private final CredentialCipher cipher;
    private final Map<AiProvider, AiGuideProvider> providers = new EnumMap<>(AiProvider.class);
    private final Clock clock;

    public CredentialService(
            CredentialStore store, CredentialCipher cipher, List<AiGuideProvider> providers, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.clock = clock;
        providers.forEach(p -> this.providers.put(p.provider(), p));
    }

    @Transactional(readOnly = true)
    public AiSettingsView settings() {
        Map<AiProvider, String> last4 = new EnumMap<>(AiProvider.class);
        store.findAll().forEach(c -> last4.put(c.provider(), c.last4()));
        List<AiSettingsView.ProviderStatus> statuses = Arrays.stream(AiProvider.values())
                .map(p -> new AiSettingsView.ProviderStatus(p, last4.get(p)))
                .toList();
        return new AiSettingsView(statuses, store.selectedProvider().orElse(null));
    }

    /**
     * 키를 검증해 저장한다.
     *
     * @throws DomainValidationException 키 형식이 잘못됨
     * @throws CredentialRejectedException 공급자가 키를 거부함
     * @throws CredentialCheckUnavailableException 일시 장애로 확인하지 못함
     */
    @Transactional
    public void register(AiProvider provider, String rawKey) {
        ApiKey key = ApiKey.of(rawKey);
        KeyCheck check = verify(provider, key);
        if (check == KeyCheck.INVALID) {
            throw new CredentialRejectedException(provider);
        }
        if (check == KeyCheck.UNAVAILABLE) {
            throw new CredentialCheckUnavailableException(provider);
        }
        store.save(provider, cipher.encrypt(key), clock.instant());
        if (store.selectedProvider().isEmpty()) {
            store.select(provider);
        }
        log.info("AI 키 등록: provider={}, key={}", provider, key);
    }

    @Transactional
    public void delete(AiProvider provider) {
        if (!store.delete(provider)) {
            return;
        }
        log.info("AI 키 삭제: provider={}", provider);
        if (store.selectedProvider().filter(provider::equals).isPresent()) {
            store.select(store.findAll().stream()
                    .map(StoredCredential::provider)
                    .findFirst()
                    .orElse(null));
        }
    }

    /** 키가 등록된 공급자만 선택할 수 있다. */
    @Transactional
    public void select(AiProvider provider) {
        boolean hasKey = store.findAll().stream().anyMatch(c -> c.provider() == provider);
        if (!hasKey) {
            throw new DomainValidationException("provider", "ai.selection.noKey");
        }
        store.select(provider);
    }

    /** 어댑터가 예상 못한 예외를 던져도 원인 메시지(키가 섞일 수 있음)는 남기지 않고 일시 장애로 본다. */
    private KeyCheck verify(AiProvider provider, ApiKey key) {
        AiGuideProvider adapter = providers.get(provider);
        if (adapter == null) {
            throw new IllegalStateException("AI 공급자 어댑터가 없습니다: " + provider);
        }
        try {
            return adapter.verifyKey(key);
        } catch (RuntimeException e) {
            log.warn(
                    "AI 키 검증 중 예외: provider={}, type={}", provider, e.getClass().getName());
            return KeyCheck.UNAVAILABLE;
        }
    }
}
