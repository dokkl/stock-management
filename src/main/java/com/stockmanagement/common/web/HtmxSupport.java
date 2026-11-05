package com.stockmanagement.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** htmx 요청 판별과 응답 헤더 헬퍼. */
public final class HtmxSupport {

    public static final String HX_REQUEST = "HX-Request";
    public static final String HX_REDIRECT = "HX-Redirect";
    public static final String HX_TRIGGER = "HX-Trigger";

    private HtmxSupport() {}

    public static boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(HX_REQUEST));
    }

    public static void redirect(HttpServletResponse response, String location) {
        response.setHeader(HX_REDIRECT, location);
    }

    public static void trigger(HttpServletResponse response, String event) {
        response.setHeader(HX_TRIGGER, event);
    }
}
