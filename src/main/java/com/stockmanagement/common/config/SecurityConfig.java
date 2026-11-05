package com.stockmanagement.common.config;

import com.stockmanagement.user.adapter.kakao.KakaoOAuth2UserService;
import com.stockmanagement.user.web.HtmxAwareAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/** 카카오 간편로그인(OAuth2 Authorization Code, research R1·R3). 비밀번호 로그인은 없다. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KakaoOAuth2UserService kakaoUserService)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/login",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/css/**",
                                "/js/**",
                                "/webjars/**",
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .anyRequest()
                        .hasRole("USER"))
                .oauth2Login(oauth -> oauth.loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoUserService))
                        .defaultSuccessUrl("/", true)
                        .failureHandler(loginFailureHandler()))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HtmxAwareAuthenticationEntryPoint()))
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(
                                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
                                                + " object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(
                                referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /** 카카오 동의 취소(access_denied)와 그 밖의 실패(state 불일치, 토큰·사용자 정보 오류)를 나눠 안내한다. */
    private static AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            boolean cancelled = exception instanceof OAuth2AuthenticationException oauth
                    && "access_denied".equals(oauth.getError().getErrorCode());
            response.sendRedirect(request.getContextPath() + "/login?error=" + (cancelled ? "cancelled" : "failed"));
        };
    }
}
