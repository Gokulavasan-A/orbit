package com.enterprise.api.security;

import java.time.Duration;

import com.enterprise.api.config.properties.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The refresh token cookie: HttpOnly, Secure, SameSite=Strict, sent only to the
 * auth endpoints, max-age = refresh token lifetime. Its value is never logged.
 */
@Component
public class RefreshTokenCookies {

    private final AuthProperties.RefreshToken settings;

    public RefreshTokenCookies(AuthProperties authProperties) {
        this.settings = authProperties.refreshToken();
    }

    public String cookieName() {
        return settings.cookieName();
    }

    public ResponseCookie create(IssuedToken refreshToken) {
        return builder(refreshToken.value()).maxAge(settings.ttl()).build();
    }

    public ResponseCookie clear() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite("Strict")
                .path(settings.cookiePath());
    }
}
