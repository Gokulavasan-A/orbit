package com.enterprise.api.repository.support;

/**
 * Common parameters of the {@code usp_*_GetPaged} procedures. The procedures
 * normalise every value themselves (page >= 1, size capped at 500, whitelisted
 * sort column), so this is passed through unchanged.
 */
public record PageQuery(int page, int pageSize, String sortColumn, String sortDirection, String searchText) {
}
