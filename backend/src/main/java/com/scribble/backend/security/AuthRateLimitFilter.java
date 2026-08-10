package com.scribble.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final RateLimiter authRateLimiter;

    public AuthRateLimitFilter(@Qualifier("authRateLimiter") RateLimiter authRateLimiter) {
        this.authRateLimiter = authRateLimiter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (!authRateLimiter.allow(clientIp)) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }


    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}