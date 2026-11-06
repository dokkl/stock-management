package com.stockmanagement.user.web;

import com.stockmanagement.common.web.HtmxSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/** 미인증: 일반 요청은 /login 리다이렉트, htmx 요청은 401 + HX-Redirect. */
public class HtmxAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final LoginUrlAuthenticationEntryPoint delegate = new LoginUrlAuthenticationEntryPoint("/login");

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException, ServletException {
        if (HtmxSupport.isHtmx(request)) {
            HtmxSupport.redirect(response, request.getContextPath() + "/login");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        delegate.commence(request, response, e);
    }
}
