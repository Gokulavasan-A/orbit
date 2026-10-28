package com.enterprise.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;

class SqlErrorTranslatorTest {

    private final SqlErrorTranslator translator = new SqlErrorTranslator();

    /** The table in enterprise-db/docs/error-codes.md. */
    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
            "50002, CONCURRENCY_CONFLICT,       409",
            "50003, INVALID_ARGUMENT,           400",
            "50004, LAST_ACTIVE_ADMIN,          409",
            "50101, AUTH_USER_INACTIVE,         401",
            "50201, SESSION_NOT_FOUND,          404",
            "50202, SESSION_INVALID,            401",
            "50203, REFRESH_TOKEN_NOT_FOUND,    401",
            "50204, REFRESH_TOKEN_REUSED,       401",
            "50205, SESSION_USER_MISMATCH,      401",
            "50301, USERNAME_EXISTS,            409",
            "50302, EMAIL_EXISTS,               409",
            "50303, USER_NOT_FOUND,             404",
            "50304, CANNOT_MODIFY_SELF,         409",
            "50401, SYSTEM_ROLE_PROTECTED,      409",
            "50402, ROLE_NAME_EXISTS,           409",
            "50403, ROLE_NOT_FOUND,             404",
            "50501, PERMISSION_NOT_FOUND,       404",
            "50502, ADMIN_ROLE_MANAGE_REQUIRED, 409"
    })
    void mapsDocumentedCodes(int sqlErrorNumber, ErrorCode expected, int expectedStatus) {
        RuntimeException result = translator.translate("usp_Test", jdbcFailure(sqlErrorNumber, "Internal text."));

        assertThat(result).isInstanceOf(BusinessException.class);
        BusinessException business = (BusinessException) result;
        assertThat(business.getErrorCode()).isEqualTo(expected);
        assertThat(business.getErrorCode().getStatus().value()).isEqualTo(expectedStatus);
        assertThat(business.getSqlErrorNumber()).isEqualTo(sqlErrorNumber);
        assertThat(business.getMessage()).contains("usp_Test").contains(String.valueOf(sqlErrorNumber));
    }

    @Test
    void everyCodeWithASqlNumberIsCoveredByTheDocumentedTable() {
        long withSqlNumber = Arrays.stream(ErrorCode.values()).filter(code -> code.getSqlErrorNumber() != null).count();
        assertThat(withSqlNumber).isEqualTo(18);
    }

    @Test
    void invalidArgumentPassesTheProcedureMessageThrough() {
        BusinessException ex = (BusinessException) translator.translate("usp_Role_Create",
                jdbcFailure(50003, " Role name must be at least 2 characters. "));

        assertThat(ex.getUserMessage()).isEqualTo("Role name must be at least 2 characters.");
    }

    @Test
    void invalidArgumentMessageIsTruncated() {
        BusinessException ex = (BusinessException) translator.translate("usp_X", jdbcFailure(50003, "x".repeat(1000)));

        assertThat(ex.getUserMessage()).hasSize(SqlErrorTranslator.MAX_USER_MESSAGE_LENGTH);
    }

    @Test
    void otherCodesUseTheFixedSafeMessage() {
        BusinessException ex = (BusinessException) translator.translate("usp_Role_Update",
                jdbcFailure(50403, "Role 17 not found in dbo.Roles."));

        assertThat(ex.getUserMessage()).isEqualTo(ErrorCode.ROLE_NOT_FOUND.getDefaultMessage());
        assertThat(ex.getUserMessage()).doesNotContain("17");
    }

    @ParameterizedTest
    @ValueSource(ints = {50000, 50001, 50099, 50599, 59999})
    void undocumentedBusinessCodesAreTechnical(int sqlErrorNumber) {
        RuntimeException result = translator.translate("usp_X", jdbcFailure(sqlErrorNumber, "?"));

        assertThat(result).isInstanceOf(TechnicalException.class);
        assertThat(((TechnicalException) result).getSqlErrorNumber()).isEqualTo(sqlErrorNumber);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 229, 1205, 2627, 2812, 8114})
    void sqlServerSystemErrorsAreTechnical(int sqlErrorNumber) {
        assertThat(translator.translate("usp_X", jdbcFailure(sqlErrorNumber, "?"))).isInstanceOf(TechnicalException.class);
    }

    @Test
    void findsSqlExceptionDeepInTheCauseChain() {
        SQLException sql = new SQLException("Session not found.", "S0001", 50201);
        RuntimeException wrapped = new IllegalStateException("outer",
                new UncategorizedSQLException("call", "{call dbo.usp_X}", sql));

        RuntimeException result = translator.translate("usp_X", wrapped);

        assertThat(result).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result).getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        assertThat(result.getCause()).isSameAs(wrapped);
    }

    @Test
    void failureWithoutSqlExceptionIsTechnical() {
        RuntimeException result = translator.translate("usp_X", new DataAccessResourceFailureException("pool exhausted"));

        assertThat(result).isInstanceOf(TechnicalException.class);
        assertThat(((TechnicalException) result).getSqlErrorNumber()).isNull();
    }

    @Test
    void selfReferencingCauseChainTerminates() {
        RuntimeException loop = new RuntimeException("a") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(translator.translate("usp_X", loop)).isInstanceOf(TechnicalException.class);
    }

    private static UncategorizedSQLException jdbcFailure(int errorNumber, String message) {
        return new UncategorizedSQLException("call", "{call dbo.usp_X}", new SQLException(message, "S0001", errorNumber));
    }
}
