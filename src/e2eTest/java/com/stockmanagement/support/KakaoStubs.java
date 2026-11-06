package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

/**
 * 카카오 로그인 대역(contracts/kakao-oauth.md "테스트 대역"). 인가 → 토큰 → 사용자 정보 → 연결 해제 경로를 흉내 낸다.
 * 인가 응답은 응답 템플릿으로 요청의 state·redirect_uri를 그대로 돌려준다(WireMock globalTemplating 필요).
 */
public final class KakaoStubs {

    public static final String ADMIN_KEY = "test-admin-key";

    private final WireMockServer server;

    public KakaoStubs(WireMockServer server) {
        this.server = server;
    }

    /** 이 카카오 사용자로 토큰 교환·사용자 정보 조회가 성공한다. nickname이 null이면 닉네임 동의를 하지 않은 응답이다. */
    public KakaoStubs user(long kakaoId, String nickname) {
        server.stubFor(post(urlPathEqualTo("/oauth/token"))
                .withRequestBody(containing("code=test-code-" + kakaoId))
                .willReturn(json(
                        200,
                        "{\"access_token\":\"token-" + kakaoId + "\",\"token_type\":\"bearer\","
                                + "\"expires_in\":21599,\"refresh_token\":\"refresh-" + kakaoId + "\"}")));
        String profile = nickname == null ? "" : ",\"kakao_account\":{\"profile\":{\"nickname\":\"" + nickname + "\"}}";
        server.stubFor(get(urlPathEqualTo("/v2/user/me"))
                .withHeader("Authorization", equalTo("Bearer token-" + kakaoId))
                .willReturn(json(200, "{\"id\":" + kakaoId + profile + "}")));
        return this;
    }

    /** 브라우저 인가 요청이 이 사용자의 인가 코드와 함께 서비스로 되돌아간다. */
    public KakaoStubs authorizeAs(long kakaoId) {
        server.stubFor(get(urlPathEqualTo("/oauth/authorize"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader(
                                "Location",
                                "{{request.query.redirect_uri}}?code=test-code-" + kakaoId
                                        + "&state={{urlEncode request.query.state}}")));
        return this;
    }

    /** 사용자가 카카오 동의 화면에서 취소한다. */
    public KakaoStubs cancelAuthorize() {
        server.stubFor(
                get(urlPathEqualTo("/oauth/authorize"))
                        .willReturn(
                                aResponse()
                                        .withStatus(302)
                                        .withHeader(
                                                "Location",
                                                "{{request.query.redirect_uri}}?error=access_denied&state={{urlEncode request.query.state}}")));
        return this;
    }

    public KakaoStubs unlinkOk() {
        server.stubFor(post(urlPathEqualTo("/v1/user/unlink")).willReturn(json(200, "{\"id\":1}")));
        return this;
    }

    public KakaoStubs unlinkFails(int status) {
        server.stubFor(post(urlPathEqualTo("/v1/user/unlink"))
                .willReturn(json(status, "{\"msg\":\"stub failure\",\"code\":-1}")));
        return this;
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
