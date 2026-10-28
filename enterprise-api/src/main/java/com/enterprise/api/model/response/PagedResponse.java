package com.enterprise.api.model.response;

import java.util.List;

/**
 * One page of a server-side paged grid, matching the {@code usp_*_GetPaged} contract.
 *
 * @param rows       rows of the requested page (empty past the last page)
 * @param totalCount total number of matching rows across all pages
 * @param page       1-based page number that was requested
 * @param pageSize   page size that was requested
 */
public record PagedResponse<T>(List<T> rows, long totalCount, int page, int pageSize) {

    public PagedResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
