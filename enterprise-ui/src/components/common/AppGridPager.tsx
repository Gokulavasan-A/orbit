import TablePagination from '@mui/material/TablePagination';

export interface AppGridPagerProps {
  /** 1-based page number. */
  page: number;
  pageSize: number;
  totalCount: number;
  pageSizeOptions: number[];
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
  disabled?: boolean;
}

/** Pager under AppGrid. Converts between the API's 1-based pages and MUI's 0-based ones. */
export function AppGridPager({
  page,
  pageSize,
  totalCount,
  pageSizeOptions,
  onPageChange,
  onPageSizeChange,
  disabled = false,
}: AppGridPagerProps) {
  return (
    <TablePagination
      component="div"
      count={totalCount}
      page={totalCount === 0 ? 0 : page - 1}
      rowsPerPage={pageSize}
      rowsPerPageOptions={pageSizeOptions}
      onPageChange={(_, zeroBasedPage) => onPageChange(zeroBasedPage + 1)}
      onRowsPerPageChange={(event) => onPageSizeChange(Number(event.target.value))}
      disabled={disabled}
      showFirstButton
      showLastButton
    />
  );
}
