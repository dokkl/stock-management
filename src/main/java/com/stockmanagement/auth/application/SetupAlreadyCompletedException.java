package com.stockmanagement.auth.application;

/** 소유자 비밀번호가 이미 설정되어 /setup을 다시 쓸 수 없다. */
public class SetupAlreadyCompletedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SetupAlreadyCompletedException() {
        super("소유자 설정이 이미 완료되었습니다");
    }
}
