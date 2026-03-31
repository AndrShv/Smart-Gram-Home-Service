package com.example.project.configs;


import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@RequiredArgsConstructor
public class FeignClientInterceptor implements RequestInterceptor {

    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void apply(RequestTemplate template) {
        String token = getJwtFromHeader();

        if (token == null) {
            token = getJwtFromCookie();
        }
        if (token == null) {
            token = getOAuth2Token();
        }

        if (token != null) {
            template.header("Authorization", "Bearer " + token);
            log.debug("Added Authorization header to Feign request");
        } else {
            log.warn("No authentication token found for Feign request");
        }
    }


    private String getOAuth2Token() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );

                if (client != null && client.getAccessToken() != null) {
                    log.debug("Using OAuth2 access token");
                    return client.getAccessToken().getTokenValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get OAuth2 token: {}", e.getMessage());
        }

        return null;
    }

    private String getJwtFromCookie() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        if ("jwt".equals(cookie.getName())) {
                            log.debug("Using JWT from cookie");
                            return cookie.getValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get JWT from cookie: {}", e.getMessage());
        }

        return null;
    }

    private String getJwtFromHeader() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    log.debug("Using JWT from Authorization header");
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get JWT from header: {}", e.getMessage());
        }
        return null;
    }

}
