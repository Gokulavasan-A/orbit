package com.enterprise.api.repository.support;

import java.util.List;

/**
 * Rows of one page plus the TotalCount column returned by a {@code usp_*_GetPaged}
 * procedure (0 when the page is empty).
 */
public record PagedResult<T>(List<T> rows, long totalCount) {

    public PagedResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
