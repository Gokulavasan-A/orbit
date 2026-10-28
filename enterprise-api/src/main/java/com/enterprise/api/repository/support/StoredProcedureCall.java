package com.enterprise.api.repository.support;

import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.enterprise.api.util.CorrelationId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlReturnResultSet;

/**
 * Fluent description of one stored procedure call, created by
 * {@link StoredProcedureExecutor#call(String)}. Not thread-safe; build one per call.
 * <p>
 * <b>Parameters are positional</b> ({@code {call dbo.usp_X(?, ?, ...)}}): declare
 * {@link #in} and {@link #out} parameters in exactly the order of the procedure's
 * signature. Trailing parameters that are left out take their procedure defaults.
 * Nothing is looked up from database metadata ({@code enterprise_app} cannot read it).
 * <p>
 * Result sets are bound in the order the procedure returns them; result sets that
 * are not declared are skipped.
 */
public final class StoredProcedureCall {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

    private final StoredProcedureExecutor executor;
    private final String procedureName;
    private final List<SqlParameter> parameters = new ArrayList<>();
    // HashMap: input values may be null (sent as SQL NULL).
    private final Map<String, Object> inValues = new HashMap<>();
    private final Set<String> names = new HashSet<>();
    private Duration timeout;

    StoredProcedureCall(StoredProcedureExecutor executor, String procedureName) {
        if (procedureName == null || !IDENTIFIER.matcher(procedureName).matches()) {
            throw new IllegalArgumentException("Invalid procedure name: " + procedureName);
        }
        this.executor = executor;
        this.procedureName = procedureName;
    }

    /** Input parameter; {@code sqlType} is a {@link Types} constant, {@code value} may be null. */
    public StoredProcedureCall in(String name, int sqlType, Object value) {
        register(name);
        parameters.add(new SqlParameter(name, sqlType));
        inValues.put(name, value);
        return this;
    }

    /** {@code @CorrelationId NVARCHAR(100)} input from the current request/job (may be null). */
    public StoredProcedureCall withCorrelationId() {
        return in("CorrelationId", Types.NVARCHAR, CorrelationId.current());
    }

    /** OUTPUT parameter, read back with {@link StoredProcedureResult#out(String, Class)}. */
    public StoredProcedureCall out(String name, int sqlType) {
        register(name);
        parameters.add(new SqlOutParameter(name, sqlType));
        return this;
    }

    /** Next result set, read back with {@link StoredProcedureResult#rows(String)}. */
    public <T> StoredProcedureCall resultSet(String name, RowMapper<T> rowMapper) {
        register(name);
        parameters.add(new SqlReturnResultSet(name, rowMapper));
        return this;
    }

    /**
     * Next result set of a {@code usp_*_GetPaged} procedure (rows carry a TotalCount
     * column), read back with {@link StoredProcedureResult#paged(String)}.
     */
    public <T> StoredProcedureCall pagedResultSet(String name, RowMapper<T> rowMapper) {
        Objects.requireNonNull(rowMapper, "rowMapper");
        return resultSet(name, (rs, rowNum) -> new PagedRow<>(rowMapper.mapRow(rs, rowNum), rs.getLong("TotalCount")));
    }

    /** Overrides {@code app.db.default-query-timeout-seconds} for this call. */
    public StoredProcedureCall timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public StoredProcedureResult execute() {
        return executor.execute(this);
    }

    String procedureName() {
        return procedureName;
    }

    List<SqlParameter> parameters() {
        return List.copyOf(parameters);
    }

    Map<String, Object> inValues() {
        return new HashMap<>(inValues);
    }

    Duration timeoutOrNull() {
        return timeout;
    }

    /** JDBC escape call string: one placeholder per IN/OUT parameter, none for result sets. */
    String callString() {
        long placeholders = parameters.stream().filter(p -> !p.isResultsParameter()).count();
        StringBuilder sql = new StringBuilder("{call dbo.").append(procedureName);
        if (placeholders > 0) {
            sql.append('(');
            for (long i = 0; i < placeholders; i++) {
                sql.append(i == 0 ? "?" : ", ?");
            }
            sql.append(')');
        }
        return sql.append('}').toString();
    }

    private void register(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid parameter name: " + name);
        }
        if (!names.add(name.toLowerCase())) {
            throw new IllegalArgumentException("Duplicate parameter or result set name: " + name);
        }
    }

    record PagedRow<T>(T row, long totalCount) {
    }
}
