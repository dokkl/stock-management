package com.stockmanagement.auth.web;

import com.stockmanagement.auth.application.SetupService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/** 소유자가 아직 없으면 /setup 외 모든 화면을 /setup으로 보낸다(contracts/web-routes.md). */
public class SetupRequiredFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PREFIXES =
            List.of("/setup", "/css/", "/js/", "/webjars/", "/actuator/health", "/error", "/favicon");

    private final SetupService setupService;

    public SetupRequiredFilter(SetupService setupService) {
        this.setupService = setupService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (setupService.isCompleted()) {
            chain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/setup");
    }
}
