package com.stockmanagement.user.web;

import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.UserId;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 로그인한 사용자(세션에 저장). 카카오 응답 원문은 담지 않고 앱 사용자 ID와 표시 이름만 둔다. 권한은 ROLE_USER 하나다.
 */
public final class AppUserPrincipal implements OAuth2User, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ROLE = "ROLE_USER";

    private final long userId;
    private final String displayName;

    public AppUserPrincipal(UserId userId, DisplayName displayName) {
        this.userId = userId.value();
        this.displayName = displayName.value();
    }

    public UserId userId() {
        return new UserId(userId);
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("userId", userId, "displayName", displayName);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
    }

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
