package com.enterprise.api.repository.support;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.enterprise.api.config.properties.DbProperties;
import com.enterprise.api.exception.SqlErrorTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.CallableStatementCreatorFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterDisposer;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlProvider;
import org.springframework.stereotype.Component;

/**
 * The single entry point for stored procedure calls from repository implementations.
 * <pre>{@code
 * PagedResult<RoleListItem> page = executor.call("usp_Role_GetPaged")
 *         .in("PageNumber", Types.INTEGER, 1)
 *         .in("PageSize", Types.INTEGER, 25)
 *         .pagedResultSet("rows", ROW_MAPPER)
 *         .execute()
 *         .paged("rows");
 * }</pre>
 * Parameters are declared explicitly (see {@link StoredProcedureCall}); no database
 * metadata is read. Calls join the current Spring transaction if there is one.
 * Failures are translated by {@link SqlErrorTranslator} into {@code BusinessException}
 * (documented {@code THROW 5xxxx} codes) or {@code TechnicalException}.
 * Only the procedure name and duration are logged, never parameter values.
 */
@Component
public class StoredProcedureExecutor {

    private static final Logger log = LoggerFactory.getLogger(StoredProcedureExecutor.class);

    private final JdbcTemplate jdbcTemplate;
    private final SqlErrorTranslator errorTranslator;
    private final int defaultTimeoutSeconds;

    public StoredProcedureExecutor(DataSource dataSource, SqlErrorTranslator errorTranslator, DbProperties dbProperties) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setSkipUndeclaredResults(true);
        this.jdbcTemplate = template;
        this.errorTranslator = errorTranslator;
        this.defaultTimeoutSeconds = dbProperties.defaultQueryTimeoutSeconds();
    }

    public StoredProcedureCall call(String procedureName) {
        return new StoredProcedureCall(this, procedureName);
    }

    StoredProcedureResult execute(StoredProcedureCall call) {
        List<SqlParameter> parameters = call.parameters();
        CallableStatementCreatorFactory factory = new CallableStatementCreatorFactory(call.callString(), parameters);
        CallableStatementCreator creator = new TimeoutCallableStatementCreator(
                factory.newCallableStatementCreator(call.inValues()), timeoutSeconds(call.timeoutOrNull()));

        long start = System.nanoTime();
        try {
            Map<String, Object> values = jdbcTemplate.call(creator, parameters);
            log.debug("{} completed in {} ms", call.procedureName(), elapsedMillis(start));
            return new StoredProcedureResult(values);
        } catch (DataAccessException ex) {
            log.debug("{} failed after {} ms", call.procedureName(), elapsedMillis(start));
            throw errorTranslator.translate(call.procedureName(), ex);
        }
    }

    private int timeoutSeconds(Duration timeout) {
        if (timeout == null) {
            return defaultTimeoutSeconds;
        }
        long seconds = timeout.toSeconds() + (timeout.toMillisPart() > 0 ? 1 : 0);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, seconds));
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Sets the query timeout on the statement. A transaction timeout, if any, still
     * takes precedence (JdbcTemplate applies it afterwards).
     */
    private record TimeoutCallableStatementCreator(CallableStatementCreator delegate, int timeoutSeconds)
            implements CallableStatementCreator, SqlProvider, ParameterDisposer {

        @Override
        public CallableStatement createCallableStatement(Connection con) throws SQLException {
            CallableStatement statement = delegate.createCallableStatement(con);
            statement.setQueryTimeout(timeoutSeconds);
            return statement;
        }

        @Override
        public String getSql() {
            return delegate instanceof SqlProvider provider ? provider.getSql() : null;
        }

        @Override
        public void cleanupParameters() {
            if (delegate instanceof ParameterDisposer disposer) {
                disposer.cleanupParameters();
            }
        }
    }
}
