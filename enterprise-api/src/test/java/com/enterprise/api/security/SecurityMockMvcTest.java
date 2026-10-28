package com.enterprise.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.config.properties.ClientIpProperties;
import com.enterprise.api.config.properties.CorsProperties;
import com.enterprise.api.config.properties.JwtProperties;
import com.enterprise.api.config.properties.RateLimitProperties;
import com.enterprise.api.config.properties.SecurityHeadersProperties;
import com.enterprise.api.controller.AuthController;
import com.enterprise.api.controller.PermissionController;
import com.enterprise.api.controller.RoleController;
import com.enterprise.api.controller.UserController;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.model.response.MeResponse;
import com.enterprise.api.model.response.RoleDetailResponse;
import com.enterprise.api.model.response.TemporaryPasswordResponse;
import com.enterprise.api.model.response.UserProfileResponse;
import com.enterprise.api.service.IAuthService;
import com.enterprise.api.service.IPermissionService;
import com.enterprise.api.service.IRoleService;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.service.IUserService;
import com.enterprise.api.service.impl.JwtTokenServiceImpl;
import com.enterprise.api.service.impl.RateLimitServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The real security chain (JWT, session check, CSRF, CORS, rate limiting, method
 * security) in front of the real controllers, with the services mocked. No database.
 */
@WebMvcTest(controllers = {AuthController.class, RoleController.class, UserController.class,
        PermissionController.class})
@ActiveProfiles("test")
@Import(SecurityMockMvcTest.SecurityTestConfig.class)
@TestPropertySource(properties = {
        "app.security.rate-limit.login-per-ip.capacity=2",
        "app.security.rate-limit.anonymous-per-ip.capacity=1000",
        "app.security.rate-limit.authenticated-per-user.capacity=1000"
})
class SecurityMockMvcTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final UUID SESSION_ID = UUID.fromString("12345678-1234-4123-8123-123456789abc");
    /** Every test uses its own user id and IP so that the shared rate-limit buckets never interfere. */
    private static final AtomicLong IDS = new AtomicLong(100);

    @TestConfiguration
    @EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, RateLimitProperties.class,
            CorsProperties.class, ClientIpProperties.class, SecurityHeadersProperties.class})
    @Import({SecurityConfig.class, JwtConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class,
            ApiErrorResponseWriter.class, ClientIpResolver.class, RefreshTokenCookies.class, RateLimitServiceImpl.class})
    static class SecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private IAuthService authService;
    @MockitoBean
    private IRoleService roleService;
    @MockitoBean
    private IUserService userService;
    @MockitoBean
    private IPermissionService permissionService;
    @MockitoBean
    private ISessionValidationService sessionValidationService;

    private long userId;
    private String ip;

    @BeforeEach
    void setUp() {
        userId = IDS.incrementAndGet();
        ip = "10.9.0." + userId % 250;
        when(sessionValidationService.isValid(any(), anyLong())).thenReturn(true);
        when(roleService.getAll(false)).thenReturn(List.of());
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(remote()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void validTokenWithPermissionIs200() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(token(List.of("ROLE_VIEW"), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void expiredTokenIs401TokenExpired() throws Exception {
        String expired = token(List.of("ROLE_VIEW"), false, Instant.now().minus(Duration.ofHours(1)));

        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void tamperedTokenIs401TokenInvalid() throws Exception {
        String token = token(List.of("ROLE_VIEW"), false);
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void revokedSessionIs401SessionInvalid() throws Exception {
        when(sessionValidationService.isValid(eq(SESSION_ID), eq(userId))).thenReturn(false);

        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(token(List.of("ROLE_VIEW"), false))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_INVALID"));
    }

    @Test
    void missingPermissionIs403() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(token(List.of("USER_VIEW"), false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void postWithoutCsrfIs403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(remote()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));

        // A bearer token does not replace the CSRF token.
        mockMvc.perform(post("/api/v1/auth/logout").with(remote()).with(bearer(token(List.of(), false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test
    void csrfEndpointSetsOneCookieAndReturnsTheSameValue() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf").with(remote()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(setCookies).hasSize(1);
        // MockMvc does not print SameSite in the header; Tomcat does. Check the cookie itself.
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(result.getResponse().getContentAsString()).contains("\"token\":\"" + cookie.getValue() + "\"");
    }

    @Test
    void postWithCsrfCookieAndHeaderIsAccepted() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/login").with(remote()).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void corsPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login").with(remote())
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, not(containsString("*"))));
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login").with(remote())
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void loginRateLimitReturns429WithRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        Cookie csrf = csrfCookie();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(loginRequest(csrf)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(loginRequest(csrf))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void passwordChangeRequiredRestrictsEverythingElse() throws Exception {
        String restricted = token(List.of("ROLE_VIEW"), true);
        when(authService.me(any())).thenReturn(new MeResponse(
                new UserProfileResponse(userId, "admin", "admin@enterprise.local", "System", "Administrator"),
                List.of("Admin"), List.of("ROLE_VIEW"), true));

        mockMvc.perform(get("/api/v1/roles").with(remote()).with(bearer(restricted)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/auth/me").with(remote()).with(bearer(restricted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        Cookie csrf = csrfCookie();
        mockMvc.perform(post("/api/v1/auth/logout-all").with(remote()).with(bearer(restricted))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(post("/api/v1/auth/logout").with(remote()).with(bearer(restricted))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=;")));
    }

    @Test
    void failedRefreshClearsTheCookie() throws Exception {
        when(authService.refresh(any(), any())).thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED));
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/refresh").with(remote()).cookie(csrf, new Cookie("refresh_token", "x"))
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    // --- Phase 5a: user, role and permission management -------------------------------

    private static final List<String> ALL_PERMISSIONS =
            List.of("USER_VIEW", "USER_CREATE", "USER_EDIT", "USER_DELETE", "ROLE_VIEW", "ROLE_MANAGE");

    /** One management endpoint with a valid body (validation runs before {@code @PreAuthorize}). */
    record Endpoint(HttpMethod method, String path, String body, String permission) {

        boolean isWrite() {
            return method != HttpMethod.GET;
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static Stream<Endpoint> managementEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/v1/users/paged?page=1&pageSize=25", null, "USER_VIEW"),
                new Endpoint(HttpMethod.GET, "/api/v1/users/5", null, "USER_VIEW"),
                new Endpoint(HttpMethod.POST, "/api/v1/users", "{\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
                        + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"roleIds\":[2]}", "USER_CREATE"),
                new Endpoint(HttpMethod.PUT, "/api/v1/users/5", "{\"email\":\"jdoe@example.com\",\"firstName\":\"Jane\","
                        + "\"lastName\":\"Doe\",\"isActive\":true,"
                        + "\"concurrencyStamp\":\"0b9f3c1e-5d8a-4c2b-9e7f-1a2b3c4d5e6f\"}", "USER_EDIT"),
                new Endpoint(HttpMethod.PUT, "/api/v1/users/5/roles", "{\"roleIds\":[2]}", "USER_EDIT"),
                new Endpoint(HttpMethod.POST, "/api/v1/users/5/reset-password", null, "USER_EDIT"),
                new Endpoint(HttpMethod.POST, "/api/v1/users/5/unlock", null, "USER_EDIT"),
                new Endpoint(HttpMethod.DELETE, "/api/v1/users/5", null, "USER_DELETE"),
                new Endpoint(HttpMethod.GET, "/api/v1/roles/5", null, "ROLE_VIEW"),
                new Endpoint(HttpMethod.POST, "/api/v1/roles", "{\"name\":\"Auditor\",\"permissionIds\":[1]}", "ROLE_MANAGE"),
                new Endpoint(HttpMethod.PUT, "/api/v1/roles/5",
                        "{\"name\":\"Auditor\",\"isActive\":true,\"rowVersion\":\"AAAAAAAAB9E=\"}", "ROLE_MANAGE"),
                new Endpoint(HttpMethod.PUT, "/api/v1/roles/5/permissions", "{\"permissionIds\":[1]}", "ROLE_MANAGE"),
                new Endpoint(HttpMethod.DELETE, "/api/v1/roles/5", null, "ROLE_MANAGE"),
                new Endpoint(HttpMethod.GET, "/api/v1/permissions", null, "ROLE_VIEW"));
    }

    static Stream<Endpoint> managementWriteEndpoints() {
        return managementEndpoints().filter(Endpoint::isWrite);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementEndpoints")
    void managementEndpointWithoutItsPermissionIs403(Endpoint endpoint) throws Exception {
        List<String> allButRequired = ALL_PERMISSIONS.stream().filter(p -> !p.equals(endpoint.permission())).toList();
        Cookie csrf = csrfCookie();

        mockMvc.perform(managementRequest(endpoint, allButRequired, csrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementEndpoints")
    void managementEndpointWithItsPermissionIsAllowed(Endpoint endpoint) throws Exception {
        when(userService.create(any(), any(), any())).thenReturn(new TemporaryPasswordResponse(5L, "jdoe", "x"));
        when(roleService.create(any(), any(), any())).thenReturn(new RoleDetailResponse(5L, "Auditor", null, false,
                true, 0, null, null, null, List.of(), List.of()));
        Cookie csrf = csrfCookie();

        mockMvc.perform(managementRequest(endpoint, List.of(endpoint.permission()), csrf))
                .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementWriteEndpoints")
    void managementWriteWithoutCsrfIs403(Endpoint endpoint) throws Exception {
        mockMvc.perform(managementRequest(endpoint, ALL_PERMISSIONS, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test
    void createUserReturns201WithLocation() throws Exception {
        when(userService.create(any(), any(), any())).thenReturn(new TemporaryPasswordResponse(77L, "jdoe", "Temp#Pass2word"));
        Endpoint create = managementEndpoints().filter(e -> e.method() == HttpMethod.POST && e.path().equals("/api/v1/users"))
                .findFirst().orElseThrow();

        mockMvc.perform(managementRequest(create, List.of("USER_CREATE"), csrfCookie()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/77"))
                .andExpect(jsonPath("$.data.temporaryPassword").value("Temp#Pass2word"));
    }

    @Test
    void invalidRowVersionOnRoleDeleteIs400() throws Exception {
        Cookie csrf = csrfCookie();

        mockMvc.perform(delete("/api/v1/roles/5").param("rowVersion", "not-base64").with(remote())
                        .with(bearer(token(List.of("ROLE_MANAGE"), false)))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createUserValidatesTheBody() throws Exception {
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/v1/users").with(remote()).with(bearer(token(List.of("USER_CREATE"), false)))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a@b\",\"email\":\"nope\",\"firstName\":\"\",\"lastName\":\"Doe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'firstName')]").exists());
    }

    private MockHttpServletRequestBuilder managementRequest(Endpoint endpoint, List<String> permissions, Cookie csrf) {
        MockHttpServletRequestBuilder request = request(endpoint.method(), endpoint.path())
                .with(remote())
                .with(bearer(token(permissions, false)));
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        if (csrf != null) {
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        }
        return request;
    }

    private org.springframework.test.web.servlet.RequestBuilder loginRequest(Cookie csrf) {
        return post("/api/v1/auth/login").with(remote()).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"x\"}");
    }

    private Cookie csrfCookie() throws Exception {
        return mockMvc.perform(get("/api/v1/auth/csrf").with(remote())).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private RequestPostProcessor remote() {
        return (MockHttpServletRequest request) -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private String token(List<String> permissions, boolean passwordChange) {
        return token(permissions, passwordChange, Instant.now());
    }

    private String token(List<String> permissions, boolean passwordChange, Instant issuedAt) {
        return new JwtTokenServiceImpl(jwtEncoder, jwtProperties, Clock.fixed(issuedAt, ZoneOffset.UTC))
                .issueAccessToken(new TokenClaims(userId, SESSION_ID, List.of("Admin"), permissions, passwordChange))
                .value();
    }
}
