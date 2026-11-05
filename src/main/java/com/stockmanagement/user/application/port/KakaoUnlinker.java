package com.stockmanagement.user.application.port;

import com.stockmanagement.user.application.UnlinkResult;
import com.stockmanagement.user.domain.KakaoId;

/** 카카오 연결 해제(contracts/kakao-oauth.md 4). 실패도 예외 없이 결과로 돌려준다. */
public interface KakaoUnlinker {

    UnlinkResult unlink(KakaoId kakaoId);
}
