package com.enterprise.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.UserSession;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.IRoleRepository;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.security.TokenClaims;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.service.ITokenService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * User and role management against the real database ({@code DB_URL}, {@code DB_USERNAME},
 * {@code DB_PASSWORD}; skipped when any is missing):
 * create role and user → assign the role → sign in with the temporary password → must
 * change it → change it → admin removes the role (the user's session ends) → reset
 * password → lock out → unlock → delete.
 * <p>
 * The administrator acts with a token signed by the test key for a <b>real</b> session of
 * the seeded {@code admin} (created through {@code usp_Session_Create}), so the admin
 * password is never touched. Names and emails get a random suffix, so runs never collide.
 * <p>
 * <b>Writes to the database.</b> The user and the role are soft-deleted at the end (also
 * when the test fails), but the API login has no table access, so the soft-deleted rows,
 * the {@code AuditLog} rows and the {@code LoginAudit} rows stay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = {
        "app.security.rate-limit.login-per-ip.capacity=1000",
        "app.security.rate-limit.login-per-username.capacity=1000"
})
class UserManagementIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IAuthRepository authRepository;
    @Autowired
    private ISessionRepository sessionRepository;
    @Autowired
    private ISessionValidationService sessionValidationService;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IRoleRepository roleRepository;
    @Autowired
    private ITokenService tokenService;
    @Autowired
    private AuthProperties authProperties;

    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private long adminId;
    private UUID adminSessionId;
    private String adminToken;
    private Cookie csrf;
    /** Set while the test owns a live user / role, so that cleanup can remove them. */
    private Long userId;
    private Long roleId;

    @BeforeAll
    void signInAdmin() {
        LoginUser admin = authRepository.findForLogin("admin").orElseThrow();
        adminId = admin.getId();
        UserSession session = sessionRepository.create(adminId, 60, "127.0.0.1", "UserManagementIntegrationTest");
        adminSessionId = session.getSessionId();
        adminToken = tokenService.issueAccessToken(new TokenClaims(adminId, adminSessionId, admin.getRoles(),
                admin.getPermissions(), false)).value();
    }

    @AfterAll
    void cleanUp() {
        if (userId != null) {
            ignoreBusinessErrors(() -> userRepository.softDelete(userId, null, adminId, null));
        }
        if (roleId != null) {
            ignoreBusinessErrors(() -> roleRepository.softDelete(roleId, null, adminId, null));
        }
        if (adminSessionId != null) {
            ignoreBusinessErrors(() -> sessionRepository.revoke(adminSessionId, "LOGOUT"));
            sessionValidationService.evictSession(adminSessionId);
        }
    }

    @Test
    void userAndRoleLifecycle() throws Exception {
        csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CSRF_COOKIE);
        assertThat(csrf).isNotNull();
        String username = "it_user_" + suffix;

        // 1. A role with USER_VIEW, and a user without roles.
        MvcResult permissions = mockMvc.perform(asAdmin(get("/api/v1/permissions")))
                .andExpect(status().isOk()).andReturn();
        long userViewId = ((Number) JsonPath.<List<Object>>read(body(permissions),
                "$.data[*].permissions[?(@.code == 'USER_VIEW')].id").getFirst()).longValue();

        MvcResult role = mockMvc.perform(asAdmin(post("/api/v1/roles"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"it-role-" + suffix + "\",\"description\":\"integration test\","
                                + "\"permissionIds\":[" + userViewId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.permissionCodes[0]").value("USER_VIEW"))
                .andReturn();
        roleId = longAt(role, "$.data.id");

        MvcResult created = mockMvc.perform(asAdmin(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"it_" + suffix + "@example.test\","
                                + "\"firstName\":\"Integration\",\"lastName\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();
        userId = longAt(created, "$.data.userId");
        String temporary1 = JsonPath.read(body(created), "$.data.temporaryPassword");
        assertThat(temporary1).hasSizeGreaterThanOrEqualTo(12);

        // 2. Assign the role.
        MvcResult assigned = mockMvc.perform(asAdmin(put("/api/v1/users/" + userId + "/roles"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].id").value(roleId))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        String stampBeforeLogin = JsonPath.read(body(assigned), "$.data.concurrencyStamp");

        // 3. Sign in with the temporary password: everything but /me and change-password is blocked.
        MvcResult login1 = mockMvc.perform(login(username, temporary1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        String userToken1 = JsonPath.read(body(login1), "$.data.accessToken");
        mockMvc.perform(asUser(get("/api/v1/users/paged"), userToken1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        // 4. Change it; USER_VIEW (from the role) now works.
        String newPassword = "It#Pass-" + suffix + "Aa1";
        MvcResult changed = mockMvc.perform(withCsrf(post("/api/v1/auth/change-password"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken1)
                        .cookie(login1.getResponse().getCookie(REFRESH_COOKIE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + temporary1 + "\",\"newPassword\":\"" + newPassword
                                + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn();
        String userToken2 = JsonPath.read(body(changed), "$.data.accessToken");
        mockMvc.perform(asUser(get("/api/v1/users/paged").param("searchText", username), userToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));

        // 5. Logins don't change the concurrency stamp: an edit based on the pre-login read succeeds.
        mockMvc.perform(asAdmin(put("/api/v1/users/" + userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it_" + suffix + "@example.test\",\"firstName\":\"Integration\","
                                + "\"lastName\":\"Tested\",\"phoneNumber\":\"+1 555 0100\",\"isActive\":true,"
                                + "\"concurrencyStamp\":\"" + stampBeforeLogin + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastName").value("Tested"));
        // ...and the old stamp is now stale.
        mockMvc.perform(asAdmin(put("/api/v1/users/" + userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it_" + suffix + "@example.test\",\"firstName\":\"Integration\","
                                + "\"lastName\":\"Again\",\"isActive\":true,"
                                + "\"concurrencyStamp\":\"" + stampBeforeLogin + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENCY_CONFLICT"));

        // 6. Removing the role ends the user's session at once.
        mockMvc.perform(asAdmin(put("/api/v1/users/" + userId + "/roles"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isEmpty());
        mockMvc.perform(asUser(get("/api/v1/auth/me"), userToken2))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_INVALID"));

        // 7. Reset the password: a new temporary password, which must be changed again.
        mockMvc.perform(asAdmin(post("/api/v1/users/" + adminId + "/reset-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SELF_PASSWORD_RESET_NOT_ALLOWED"));
        MvcResult reset = mockMvc.perform(asAdmin(post("/api/v1/users/" + userId + "/reset-password")))
                .andExpect(status().isOk())
                .andReturn();
        String temporary2 = JsonPath.read(body(reset), "$.data.temporaryPassword");
        assertThat(temporary2).isNotEqualTo(temporary1);
        mockMvc.perform(login(username, newPassword)).andExpect(status().isUnauthorized());
        mockMvc.perform(login(username, temporary2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        // 8. Lock the account with failed sign-ins, then unlock it.
        int maxAttempts = authProperties.lockout().maxFailedAttempts();
        for (int i = 1; i < maxAttempts; i++) {
            mockMvc.perform(login(username, "Wrong#Password1")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(username, "Wrong#Password1"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        mockMvc.perform(login(username, temporary2)).andExpect(status().isLocked());
        mockMvc.perform(asAdmin(get("/api/v1/users/" + userId)))
                .andExpect(jsonPath("$.data.isLockedOut").value(true));

        mockMvc.perform(asAdmin(post("/api/v1/users/" + userId + "/unlock")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLockedOut").value(false));
        mockMvc.perform(login(username, temporary2)).andExpect(status().isOk());

        // 9. Delete the user (not yourself), then the role.
        mockMvc.perform(asAdmin(delete("/api/v1/users/" + adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SELF_DELETE_NOT_ALLOWED"));
        mockMvc.perform(asAdmin(delete("/api/v1/users/" + userId)))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/v1/users/" + userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        userId = null;

        mockMvc.perform(asAdmin(delete("/api/v1/roles/" + roleId)))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/v1/roles/" + roleId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_FOUND"));
        roleId = null;
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return asUser(request, adminToken);
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, String accessToken) {
        return withCsrf(request).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private MockHttpServletRequestBuilder login(String username, String password) {
        return withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(csrf).header(CSRF_HEADER, csrf.getValue());
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static long longAt(MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }

    private static void ignoreBusinessErrors(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (BusinessException ignored) {
            // Already gone.
        }
    }
}
