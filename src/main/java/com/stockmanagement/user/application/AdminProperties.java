package com.stockmanagement.user.application;

import com.stockmanagement.user.domain.AdminPolicy;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.admin: 관리자 카카오 회원번호 목록(ADMIN_KAKAO_IDS, 쉼표 구분). 비어 있으면 관리자가 없다. */
@ConfigurationProperties("app.admin")
public record AdminProperties(List<Long> kakaoIds) {

    public AdminPolicy policy() {
        return AdminPolicy.of(kakaoIds);
    }
}
