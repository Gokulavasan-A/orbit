package com.enterprise.api.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/v1/roles/paged}. Missing values get the
 * procedure's defaults. Unknown sort columns fall back to the default sort in
 * the procedure, so only the shape is validated here.
 */
public record RolePageRequest(
        @Min(value = 1, message = "must be at least 1")
        Integer page,

        @Min(value = 1, message = "must be between 1 and 500")
        @Max(value = 500, message = "must be between 1 and 500")
        Integer pageSize,

        @Pattern(regexp = "^[A-Za-z]{1,50}$", message = "must be a column id")
        String sortColumn,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "must be ASC or DESC")
        String sortDirection,

        @Size(max = 200, message = "must be at most 200 characters")
        String searchText,

        Boolean isActive) {

    public static final int DEFAULT_PAGE_SIZE = 25;

    public RolePageRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        sortColumn = sortColumn == null || sortColumn.isBlank() ? "name" : sortColumn;
        sortDirection = sortDirection == null || sortDirection.isBlank() ? "ASC" : sortDirection;
        searchText = searchText == null || searchText.isBlank() ? null : searchText.strip();
    }
}
