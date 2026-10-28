/** One entry of `ApiResponse.error.fieldErrors`. `field` is the request property name. */
export interface FieldErrorDetail {
  field: string;
  message: string;
}

/** `ApiResponse.error`. Branch on `code`, never on `message`. */
export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: FieldErrorDetail[];
}

/** The envelope every API response uses, success or failure. */
export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiErrorBody | null;
  correlationId: string | null;
  timestamp: string;
}

/** One page of a server-side paged grid (`PagedResponse<T>` on the API). */
export interface PagedResponse<T> {
  rows: T[];
  totalCount: number;
  /** 1-based page number. */
  page: number;
  pageSize: number;
}

export type SortDirection = 'ASC' | 'DESC';

/** Query parameters shared by the `/paged` endpoints. `sortColumn` is the grid column id. */
export interface PageQuery {
  page: number;
  pageSize: number;
  sortColumn?: string;
  sortDirection?: SortDirection;
  searchText?: string;
}
