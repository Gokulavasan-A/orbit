package com.enterprise.api.repository.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Result sets and OUTPUT values of one stored procedure call, by declared name. */
public final class StoredProcedureResult {

    private final Map<String, Object> values;

    StoredProcedureResult(Map<String, Object> values) {
        this.values = values;
    }

    /** Rows of a declared result set; empty if the procedure returned none. */
    @SuppressWarnings("unchecked")
    public <T> List<T> rows(String resultSetName) {
        Object value = values.get(resultSetName);
        return value == null ? List.of() : (List<T>) value;
    }

    public <T> Optional<T> firstRow(String resultSetName) {
        List<T> rows = rows(resultSetName);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    /** Rows and TotalCount of a result set declared with {@code pagedResultSet}. */
    public <T> PagedResult<T> paged(String resultSetName) {
        List<StoredProcedureCall.PagedRow<T>> rows = rows(resultSetName);
        long totalCount = rows.isEmpty() ? 0 : rows.getFirst().totalCount();
        return new PagedResult<>(rows.stream().map(StoredProcedureCall.PagedRow::row).toList(), totalCount);
    }

    public <T> T out(String parameterName, Class<T> type) {
        Object value = values.get(parameterName);
        return value == null ? null : type.cast(value);
    }
}
