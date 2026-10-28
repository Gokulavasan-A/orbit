package com.enterprise.api.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Request matchers for the auth endpoints, shared by the security chain and the filters. */
public final class AuthPaths {

    public static final String BASE = "/api/v1/auth";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    public static final RequestMatcher CSRF = PATHS.matcher(HttpMethod.GET, BASE + "/csrf");
    public static final RequestMatcher LOGIN = PATHS.matcher(HttpMethod.POST, BASE + "/login");
    public static final RequestMatcher REFRESH = PATHS.matcher(HttpMethod.POST, BASE + "/refresh");
    public static final RequestMatcher ME = PATHS.matcher(HttpMethod.GET, BASE + "/me");
    public static final RequestMatcher CHANGE_PASSWORD = PATHS.matcher(HttpMethod.POST, BASE + "/change-password");
    public static final RequestMatcher LOGOUT = PATHS.matcher(HttpMethod.POST, BASE + "/logout");

    /** Anonymous endpoints. A bearer token sent to them is ignored, so a stale token cannot block a login. */
    public static final RequestMatcher PUBLIC = new OrRequestMatcher(CSRF, LOGIN, REFRESH);

    /** The only endpoints allowed while the token carries {@code pwd_change = true}. */
    public static final RequestMatcher ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED = new OrRequestMatcher(ME, CHANGE_PASSWORD, LOGOUT);

    private AuthPaths() {
    }
}
