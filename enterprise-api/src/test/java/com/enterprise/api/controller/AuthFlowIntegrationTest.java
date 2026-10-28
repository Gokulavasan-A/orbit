package com.enterprise.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Types;
import java.util.UUID;

import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole auth flow against the real database ({@code DB_URL}, {@code DB_USERNAME},
 * {@code DB_PASSWORD}; skipped when any is missing), signing in as the seeded admin:
 * login → me → refresh → reuse of the old refresh token (401, all sessions revoked)
 * → login → change password → logout.
 * <p>
 * <b>Writes to the database.</b> Before and after the test the admin password is
 * reset to the V11 seed value ({@code ChangeMe@123!}) with {@code usp_User_ResetPassword},
 * which also sets MustChangePassword = 1, clears any lockout and revokes the admin's
 * sessions. PasswordChangedOn changes, and the LoginAudit / AuditLog rows stay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AuthFlowIntegrationTest {

    private static final String ADMIN = "admin";
    /** DEV-ONLY seed password and its BCrypt hash, from enterprise-db/migrations/V11__seed_data.sql. */
    private static final String SEED_PASSWORD = "ChangeMe@123!";
    private static final String SEED_HASH = "$2a$12$NC.nL3Xt/p0tIx1/g.W8bOJVHDXm6hyTorOvv3atbkMGGILvgHLWK";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StoredProcedureExecutor executor;
    @Autowired
    private IAuthRepository authRepository;

    private long adminId;
    private Cookie csrf;

    @BeforeAll
    void resetAdminBefore() {
        LoginUser admin = authRepository.findForLogin(ADMIN).orElseThrow();
        adminId = admin.getId();
        restoreSeedPassword();
    }

    @AfterAll
    void resetAdminAfter() {
        restoreSeedPassword();
    }

    @Test
    void fullAuthFlow() throws Exception {
        csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CSRF_COOKIE);
        assertThat(csrf).isNotNull();

        // 1. Login: the seeded admin must change the password first.
        MvcResult login = mockMvc.perform(login(SEED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(ADMIN))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andReturn();
        String access1 = accessToken(login);
        Cookie refresh1 = refreshCookie(login);
        assertThat(login.getResponse().getContentAsString()).doesNotContain(refresh1.getValue());

        // 2. /me is allowed while the password change is pending; other endpoints are not.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(adminId))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        mockMvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + access1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        // 3. Refresh rotates the cookie.
        MvcResult refreshed = mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")).cookie(refresh1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        String access2 = accessToken(refreshed);
        Cookie refresh2 = refreshCookie(refreshed);
        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        // 4. Reusing the old refresh token is detected: 401 and every session of the user is revoked.
        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")).cookie(refresh1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access2))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_INVALID"));
        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")).cookie(refresh2))
                .andExpect(status().isUnauthorized());

        // 5. Login again and change the password.
        MvcResult login2 = mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn();
        String access3 = accessToken(login2);
        String newPassword = "Flow#Test-" + UUID.randomUUID().toString().substring(0, 8) + "Aa1";

        MvcResult changed = mockMvc.perform(withCsrf(post("/api/v1/auth/change-password"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access3)
                        .cookie(refreshCookie(login2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + SEED_PASSWORD + "\",\"newPassword\":\"" + newPassword
                                + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn();
        String access4 = accessToken(changed);
        assertThat(refreshCookie(changed)).isNotNull();

        mockMvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + access4))
                .andExpect(status().isOk());
        mockMvc.perform(login(SEED_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // 6. Logout ends the session immediately (the validation cache is evicted).
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")).header(HttpHeaders.AUTHORIZATION, "Bearer " + access4))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access4))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_INVALID"));
    }

    private MockHttpServletRequestBuilder login(String password) {
        return withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + password + "\"}");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(csrf).header(CSRF_HEADER, csrf.getValue());
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        return cookie;
    }

    /** Back to the V11 seed state: seed hash, MustChangePassword = 1, no lockout, no sessions. */
    private void restoreSeedPassword() {
        executor.call("usp_User_ResetPassword")
                .in("UserId", Types.BIGINT, adminId)
                .in("NewPasswordHash", Types.NVARCHAR, SEED_HASH)
                .in("ActorUserId", Types.BIGINT, adminId)
                .in("IpAddress", Types.NVARCHAR, null)
                .withCorrelationId()
                .execute();
    }
}
