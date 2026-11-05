package com.stockmanagement.common.web;

import com.stockmanagement.common.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 오류 처리(contracts/web-routes.md). 일반 요청은 오류 페이지, htmx 요청은 토스트 프래그먼트를 반환한다.
 * 내부 정보(스택 트레이스, 예외 메시지)는 화면에 노출하지 않는다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public String notFound(Exception e, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.debug("Not found: {} {}", request.getMethod(), request.getRequestURI(), e);
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return render(request, response, model, "error.notFound", "error/404");
    }

    @ExceptionHandler(Exception.class)
    public String serverError(Exception e, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.error("Unhandled error: {} {}", request.getMethod(), request.getRequestURI(), e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return render(request, response, model, "error.server", "error/500");
    }

    private static String render(
            HttpServletRequest request, HttpServletResponse response, Model model, String messageKey, String page) {
        if (HtmxSupport.isHtmx(request)) {
            response.setHeader("HX-Reswap", "none");
            model.addAttribute("toastKey", messageKey);
            model.addAttribute("toastLevel", "error");
            return "fragments/toast :: toast";
        }
        return page;
    }
}
