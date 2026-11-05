package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 공급자 호출 설정(research R4). 공용 키는 없다(FR-023): 키는 요청마다 사용자 키를 쓴다.
 *
 * @param models 공급자별 기본 모델
 * @param baseUrls 공급자별 API 주소. 없으면 공식 주소(테스트에서는 WireMock 주소로 바꾼다)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(Duration timeout, Map<AiProvider, String> models, Map<AiProvider, String> baseUrls) {

    public AiProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
        baseUrls = baseUrls == null ? Map.of() : Map.copyOf(baseUrls);
    }

    String model(AiProvider provider) {
        String model = models.get(provider);
        if (model == null) {
            throw new IllegalStateException(
                    "app.ai.models." + provider.name().toLowerCase(java.util.Locale.ROOT) + " 설정이 없습니다");
        }
        return model;
    }

    String baseUrl(AiProvider provider, String defaultUrl) {
        return baseUrls.getOrDefault(provider, defaultUrl);
    }
}
