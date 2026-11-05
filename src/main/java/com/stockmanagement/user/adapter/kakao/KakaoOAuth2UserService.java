package com.stockmanagement.user.adapter.kakao;

import com.stockmanagement.user.application.UserService;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import com.stockmanagement.user.web.AppUserPrincipal;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 카카오 사용자 정보(contracts/kakao-oauth.md 3)를 앱 사용자로 바꾼다. 회원번호(id)는 필수, 닉네임은 선택 동의다.
 * 세션에는 카카오 응답 원문 대신 앱 사용자 ID와 표시 이름만 남긴다. 토큰·응답 본문은 로그에 남기지 않는다.
 */
@Component
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    private final UserService userService;

    public KakaoOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User kakao;
        try {
            kakao = delegate.loadUser(request);
        } catch (IllegalArgumentException e) {
            // 사용자 정보에 회원번호(user-name-attribute id)가 없으면 기본 구현이 IllegalArgumentException을 던진다
            throw invalid();
        }
        KakaoId kakaoId = kakaoId(kakao.getAttributes().get("id"));
        DisplayName name = DisplayName.of(nickname(kakao.getAttributes()));
        UserId userId = userService.signIn(kakaoId, name);
        return new AppUserPrincipal(userId, name);
    }

    private static KakaoId kakaoId(Object raw) {
        try {
            if (raw instanceof Number number) {
                return new KakaoId(number.longValue());
            }
            if (raw instanceof String text) {
                return new KakaoId(Long.parseLong(text));
            }
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        throw invalid();
    }

    private static String nickname(Map<String, Object> attributes) {
        if (attributes.get("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }
        return null;
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info", "카카오 회원번호가 없습니다", null));
    }
}
