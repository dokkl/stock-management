package com.stockmanagement.auth.web;

import com.stockmanagement.auth.application.LoginAttemptService;
import com.stockmanagement.auth.domain.LoginDecision;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** 사용자 이름 없이 소유자 비밀번호만 검증한다(FR-001). 잠금 정책은 LoginAttemptService가 적용한다. */
@Component
public class OwnerAuthenticationProvider implements AuthenticationProvider {

    public static final String OWNER_NAME = "owner";

    private final LoginAttemptService loginAttemptService;

    public OwnerAuthenticationProvider(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? null : credentials.toString();
        LoginDecision decision = loginAttemptService.attempt(password);
        return switch (decision) {
            case SUCCESS ->
                UsernamePasswordAuthenticationToken.authenticated(
                        OWNER_NAME, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
            case LOCKED -> throw new LockedException("locked");
            case BAD_CREDENTIALS -> throw new BadCredentialsException("bad credentials");
        };
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
