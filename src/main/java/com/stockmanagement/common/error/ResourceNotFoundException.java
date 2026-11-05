package com.stockmanagement.common.error;

/** 요청한 리소스가 없다 → 404 (contracts/web-routes.md 공통 오류 표). */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
