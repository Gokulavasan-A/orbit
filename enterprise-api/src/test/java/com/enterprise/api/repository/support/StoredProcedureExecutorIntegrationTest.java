package com.enterprise.api.repository.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Types;
import java.util.List;

import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.util.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs {@link StoredProcedureExecutor} against the real database as the API login
 * ({@code DB_URL}, {@code DB_USERNAME}, {@code DB_PASSWORD}). Skipped when any of
 * them is missing. Read-only, except one write that runs in a rolled-back test
 * transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class StoredProcedureExecutorIntegrationTest {

    @Autowired
    private StoredProcedureExecutor executor;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void readsSingleResultSet() {
        List<String> names = executor.call("usp_Role_GetAll")
                .in("IncludeInactive", Types.BIT, true)
                .resultSet("roles", (rs, n) -> rs.getString("Name") + ":" + rs.getBytes("RowVersion").length)
                .execute()
                .rows("roles");

        // Seeded system roles, each with an 8-byte RowVersion.
        assertThat(names).contains("Admin:8", "User:8");
    }

    @Test
    void readsPagedResultWithTotalCount() {
        // Dynamic SQL inside: also proves WITH EXECUTE AS OWNER works for enterprise_app.
        PagedResult<String> page = executor.call("usp_Role_GetPaged")
                .in("PageNumber", Types.INTEGER, 1)
                .in("PageSize", Types.INTEGER, 1)
                .in("SortColumn", Types.NVARCHAR, "name")
                .in("SortDirection", Types.NVARCHAR, "ASC")
                .pagedResultSet("rows", (rs, n) -> rs.getString("Name"))
                .execute()
                .paged("rows");

        assertThat(page.rows()).hasSize(1);
        assertThat(page.totalCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void readsMultipleResultSetsInOrder() {
        StoredProcedureResult result = executor.call("usp_Auth_GetUserForLogin")
                .in("UsernameOrEmail", Types.NVARCHAR, "admin")
                .resultSet("user", (rs, n) -> rs.getString("Username"))
                .resultSet("roles", (rs, n) -> rs.getString("RoleName"))
                .resultSet("permissions", (rs, n) -> rs.getString("PermissionCode"))
                .execute();

        assertThat(result.<String>rows("user")).containsExactly("admin");
        assertThat(result.<String>rows("roles")).contains("Admin");
        assertThat(result.<String>rows("permissions")).contains("ROLE_MANAGE", "USER_VIEW");
    }

    @Test
    @Transactional // rolled back after the test: the audit row is never committed
    void readsOutputParameterAndJoinsTransaction() {
        MDC.put(CorrelationId.MDC_KEY, "it-output-param");

        Long auditLogId = executor.call("usp_AuditLog_Insert")
                .in("ActorUserId", Types.BIGINT, null)
                .in("Action", Types.NVARCHAR, "IT_TEST")
                .in("EntityName", Types.NVARCHAR, "StoredProcedureExecutorIntegrationTest")
                .in("EntityId", Types.NVARCHAR, null)
                .in("OldValues", Types.NVARCHAR, null)
                .in("NewValues", Types.NVARCHAR, "{\"test\":true}")
                .in("IpAddress", Types.NVARCHAR, null)
                .withCorrelationId()
                .out("AuditLogId", Types.BIGINT)
                .execute()
                .out("AuditLogId", Long.class);

        assertThat(auditLogId).isPositive();
    }

    @Test
    void translatesBusinessErrors() {
        assertThatThrownBy(() -> executor.call("usp_Session_CleanupExpired")
                .in("RetentionDays", Types.INTEGER, -1)
                .execute())
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
                    assertThat(ex.getSqlErrorNumber()).isEqualTo(50003);
                    assertThat(ex.getUserMessage()).contains("RetentionDays");
                });
    }

    @Test
    void translatesUnknownErrorsAsTechnical() {
        assertThatThrownBy(() -> executor.call("usp_DoesNotExist").execute())
                .isInstanceOfSatisfying(TechnicalException.class,
                        ex -> assertThat(ex.getSqlErrorNumber()).isEqualTo(2812));
    }
}
