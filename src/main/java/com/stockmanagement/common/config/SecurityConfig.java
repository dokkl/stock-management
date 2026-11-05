package com.stockmanagement.common.config;

import com.stockmanagement.auth.application.SetupService;
import com.stockmanagement.auth.web.HtmxAwareAuthenticationEntryPoint;
import com.stockmanagement.auth.web.SetupRequiredFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SetupService setupService) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/setup", "/login", "/css/**", "/js/**", "/webjars/**", "/actuator/health", "/error")
                        .permitAll()
                        .anyRequest()
                        .hasRole("OWNER"))
                .formLogin(form -> form.loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HtmxAwareAuthenticationEntryPoint()))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
                                        + " frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(
                                referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .addFilterBefore(new SetupRequiredFilter(setupService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String query = exception instanceof LockedException ? "locked" : "error";
            response.sendRedirect(request.getContextPath() + "/login?" + query);
        };
    }
}
