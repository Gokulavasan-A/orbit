package com.enterprise.api.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/v1/users/paged}. Missing values get the
 * procedure's defaults. Unknown sort columns fall back to the default sort in
 * the procedure, so only the shape is validated here.
 */
public record UserPageRequest(
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

        Boolean isActive,

        @Min(value = 1, message = "must be a role id")
        Long roleId) {

    public static final int DEFAULT_PAGE_SIZE = 25;

    public UserPageRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        sortColumn = sortColumn == null || sortColumn.isBlank() ? "createdOn" : sortColumn;
        sortDirection = sortDirection == null || sortDirection.isBlank() ? "DESC" : sortDirection;
        searchText = searchText == null || searchText.isBlank() ? null : searchText.strip();
    }
}
