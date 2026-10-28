import Search from '@mui/icons-material/Search';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import {
  CellStyleModule,
  ClientSideRowModelModule,
  ColumnApiModule,
  ModuleRegistry,
  ValidationModule,
  type ColDef,
  type GetRowIdParams,
  type SortChangedEvent,
} from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError } from '../../api/apiError';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { agGridTheme } from '../../theme/agGridTheme';
import type { PageQuery, PagedResponse, SortDirection } from '../../types/api';
import { AppGridPager } from './AppGridPager';

// Only the modules AppGrid uses (AllCommunityModule would add ~1 MB). In dev, ValidationModule
// logs the name of any module a new grid option needs: add it here.
ModuleRegistry.registerModules([
  ClientSideRowModelModule,
  ColumnApiModule,
  CellStyleModule,
  ...(import.meta.env.DEV ? [ValidationModule] : []),
]);

export interface AppGridSort {
  /** Column id, which must be a sort column id the API accepts (the response field name). */
  column: string;
  direction: SortDirection;
}

export interface AppGridProps<T> {
  /** Column definitions. `field` (or `colId`) is sent as `sortColumn`, so it must match the API. */
  columnDefs: ColDef<T>[];
  /**
   * Loads one page from the API. Keep it stable (module function or useCallback): a new function
   * reloads the grid.
   */
  fetchPage: (query: PageQuery) => Promise<PagedResponse<T>>;
  /** Stable row id. */
  getRowId: (row: T) => string;
  /** Sort used until the user sorts. Omit to use the API's default. */
  defaultSort?: AppGridSort;
  /** @default 25 */
  initialPageSize?: number;
  /** @default [10, 25, 50, 100] */
  pageSizeOptions?: number[];
  /** Shows the debounced search box when set. */
  searchPlaceholder?: string;
  /** Content next to the search box, e.g. filters or a Create button. */
  toolbar?: ReactNode;
  /** Message of the empty overlay. @default 'No records found.' */
  emptyMessage?: string;
  /** Grid height in px. @default 520 */
  height?: number;
  /** Change it to reload the current page (e.g. after a save). */
  reloadKey?: number;
  /** Accessible name of the search box and grid. */
  ariaLabel?: string;
}

/** Search text debounce (ms). */
export const SEARCH_DEBOUNCE_MS = 300;

interface PagingState {
  page: number;
  pageSize: number;
  sort: AppGridSort | undefined;
  /** The search text this page number belongs to; a new search starts at page 1. */
  searchText: string;
}

interface LoadResult<T> {
  key: string;
  rows: T[];
  totalCount: number;
  error: string | null;
}

/**
 * AG Grid wrapper for server-side paged lists (`PagedResponse` endpoints).
 *
 * AG Grid's Server-Side Row Model is Enterprise-only, so this uses the client-side row model with
 * one server page as row data. Paging (pager below), sorting (header clicks) and the debounced
 * search are mapped to page/pageSize/sortColumn/sortDirection/searchText and sent to the API. The
 * grid itself never sorts or pages.
 */
export function AppGrid<T>({
  columnDefs,
  fetchPage,
  getRowId,
  defaultSort,
  initialPageSize = 25,
  pageSizeOptions = [10, 25, 50, 100],
  searchPlaceholder,
  toolbar,
  emptyMessage = 'No records found.',
  height = 520,
  reloadKey = 0,
  ariaLabel,
}: AppGridProps<T>) {
  const [searchInput, setSearchInput] = useState('');
  const searchText = useDebouncedValue(searchInput.trim(), SEARCH_DEBOUNCE_MS);
  const [paging, setPaging] = useState<PagingState>({
    page: 1,
    pageSize: initialPageSize,
    sort: defaultSort,
    searchText: '',
  });
  const page = paging.searchText === searchText ? paging.page : 1;
  const sortColumn = paging.sort?.column;
  const sortDirection = paging.sort?.direction;

  // Primitive deps: an unchanged sort or page from the grid must not trigger a new request.
  const query = useMemo<PageQuery>(
    () => ({
      page,
      pageSize: paging.pageSize,
      sortColumn,
      sortDirection,
      searchText: searchText || undefined,
    }),
    [page, paging.pageSize, sortColumn, sortDirection, searchText],
  );
  const queryKey = `${JSON.stringify(query)}#${reloadKey}`;

  const [result, setResult] = useState<LoadResult<T>>({ key: '', rows: [], totalCount: 0, error: null });
  const loading = result.key !== queryKey;

  useEffect(() => {
    let cancelled = false;
    fetchPage(query).then(
      (response) => {
        if (!cancelled) {
          setResult({ key: queryKey, rows: response.rows, totalCount: response.totalCount, error: null });
        }
      },
      (error: unknown) => {
        if (!cancelled) {
          setResult({ key: queryKey, rows: [], totalCount: 0, error: ApiError.from(error).message });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [fetchPage, query, queryKey]);

  const gridColumns = useMemo<ColDef<T>[]>(
    () =>
      columnDefs.map((col) => {
        const id = col.colId ?? (col.field as string | undefined);
        return defaultSort && id === defaultSort.column
          ? { ...col, initialSort: defaultSort.direction === 'ASC' ? 'asc' : 'desc' }
          : col;
      }),
    [columnDefs, defaultSort],
  );

  const defaultColDef = useMemo<ColDef<T>>(
    () => ({
      sortable: true,
      resizable: true,
      sortingOrder: ['asc', 'desc', null],
      // The server sorts; keep the rows in the order they came in.
      comparator: () => 0,
    }),
    [],
  );

  const handleSortChanged = (event: SortChangedEvent<T>) => {
    const sorted = event.api.getColumnState().find((column) => column.sort);
    const sort: AppGridSort | undefined = sorted?.sort
      ? { column: sorted.colId, direction: sorted.sort === 'asc' ? 'ASC' : 'DESC' }
      : defaultSort;
    setPaging((p) => ({ ...p, sort, page: 1, searchText }));
  };

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      {(searchPlaceholder !== undefined || toolbar) && (
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ p: 1.5, alignItems: { sm: 'center' } }}
        >
          {searchPlaceholder !== undefined && (
            <TextField
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={searchPlaceholder}
              type="search"
              sx={{ maxWidth: { sm: 320 } }}
              slotProps={{
                htmlInput: { 'aria-label': ariaLabel ? `Search ${ariaLabel}` : 'Search', maxLength: 200 },
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
            />
          )}
          <Box sx={{ flexGrow: 1 }} />
          {toolbar}
        </Stack>
      )}
      {result.error && !loading && (
        <Alert severity="error" sx={{ mx: 1.5, mb: 1.5 }}>
          {result.error}
        </Alert>
      )}
      <Box sx={{ height }} aria-label={ariaLabel} role="region">
        <AgGridReact<T>
          theme={agGridTheme}
          rowData={result.rows}
          columnDefs={gridColumns}
          defaultColDef={defaultColDef}
          getRowId={(params: GetRowIdParams<T>) => getRowId(params.data)}
          loading={loading}
          overlayNoRowsTemplate={`<span>${escapeHtml(emptyMessage)}</span>`}
          onSortChanged={handleSortChanged}
          suppressCellFocus
          suppressMultiSort
          suppressDragLeaveHidesColumns
        />
      </Box>
      <AppGridPager
        page={page}
        pageSize={paging.pageSize}
        totalCount={result.totalCount}
        pageSizeOptions={pageSizeOptions}
        disabled={loading}
        onPageChange={(next) => setPaging((p) => ({ ...p, page: next, searchText }))}
        onPageSizeChange={(pageSize) => setPaging((p) => ({ ...p, pageSize, page: 1, searchText }))}
      />
    </Paper>
  );
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
