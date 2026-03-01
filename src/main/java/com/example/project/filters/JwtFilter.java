package com.example.project.filters;

import com.example.project.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractJwtFromRequest(request);
        String email = null;

        log.debug("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        log.debug("Token found: {}", token != null ? "YES (first 20 chars: " + token.substring(0, Math.min(20, token.length())) + "...)" : "NO");

        if (token != null) {
            try {
                if (jwtUtil.validateToken(token)) {
                    email = jwtUtil.getEmailFromToken(token);
                    log.debug("Token is valid. Email: {}", email);
                } else {
                    log.warn("Token validation failed");
                }
            } catch (Exception e) {
                log.error("Error validating token: {}", e.getMessage());
                token = null;
            }
        } else {
            log.warn("No JWT token found in request");
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<String> authorities = jwtUtil.getAuthoritiesFromToken(token);
            String userId = jwtUtil.getUserIdFromToken(token).toString();

            List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            log.debug("User from token: {}, userId: {}, authorities: {}", email, userId, grantedAuthorities);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, token, grantedAuthorities);

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("✅ Authentication set for user: {} (userId: {}) with roles: {}", email, userId, grantedAuthorities);
        }else if (email == null) {
            log.warn("❌ Email is null, authentication not set");
        } else {
            log.debug("Authentication already exists in context");
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        log.debug("Authorization header: {}", bearerToken != null ? bearerToken.substring(0, Math.min(30, bearerToken.length())) + "..." : "null");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("Extracted token from Bearer header");
            return token;
        }

        if (request.getCookies() != null) {
            log.debug("Checking cookies for JWT...");
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> {
                        log.debug("Cookie found: {} = {}", cookie.getName(), cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())) + "...");
                        return "jwt".equals(cookie.getName());
                    })
                    .map(cookie -> {
                        log.debug("JWT cookie found!");
                        return cookie.getValue();
                    })
                    .findFirst()
                    .orElse(null);
        }

        log.debug("No cookies in request");
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        boolean shouldSkip =
                path.startsWith("/auth/") ||
                        path.startsWith("/oauth2/") ||
                        path.startsWith("/actuator/") ||
                        path.startsWith("/css/") ||
                        path.startsWith("/js/") ||
                        path.startsWith("/images/") ||
                        path.startsWith("/webjars/") ||
                        path.equals("/favicon.ico");

        if (path.startsWith("/actuator/")) {
            log.info("🔥 ACTUATOR PATH DETECTED: {}, skipping filter", path);
        }

        log.debug("Path: {}, shouldNotFilter: {}", path, shouldSkip);
        return shouldSkip;
    }
}