package com.stockmanagement.user.application;

/**
 * 카카오 연결 해제 결과.
 *
 * @param reason 실패 사유(HTTP 상태 등, 응답 본문·키 미포함). 성공이면 null
 */
public record UnlinkResult(boolean unlinked, String reason) {

    public static UnlinkResult ok() {
        return new UnlinkResult(true, null);
    }

    public static UnlinkResult failed(String reason) {
        return new UnlinkResult(false, reason);
    }
}
