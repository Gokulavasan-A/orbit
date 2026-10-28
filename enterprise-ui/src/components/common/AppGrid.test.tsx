import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ColDef } from 'ag-grid-community';
import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import type { PageQuery, PagedResponse } from '../../types/api';
import { AppGrid, type AppGridSort } from './AppGrid';

interface Row {
  id: number;
  name: string;
}

const columns: ColDef<Row>[] = [{ field: 'name', headerName: 'Name' }];
const defaultSort: AppGridSort = { column: 'name', direction: 'ASC' };

function pageOf(query: PageQuery, totalCount = 60): PagedResponse<Row> {
  const start = (query.page - 1) * query.pageSize;
  const rows = Array.from({ length: Math.max(0, Math.min(query.pageSize, totalCount - start)) }, (_, i) => ({
    id: start + i + 1,
    name: `Role ${start + i + 1}`,
  }));
  return { rows, totalCount, page: query.page, pageSize: query.pageSize };
}

function renderGrid(fetchPage = vi.fn((q: PageQuery) => Promise.resolve(pageOf(q)))) {
  renderWithProviders(
    <AppGrid<Row>
      ariaLabel="roles"
      columnDefs={columns}
      fetchPage={fetchPage}
      getRowId={(row) => String(row.id)}
      defaultSort={defaultSort}
      searchPlaceholder="Search roles"
    />,
  );
  return fetchPage;
}

describe('AppGrid', () => {
  it('loads the first page with the default sort', async () => {
    const fetchPage = renderGrid();

    expect(await screen.findByText('1–25 of 60')).toBeInTheDocument();
    expect(fetchPage).toHaveBeenCalledTimes(1);
    expect(fetchPage).toHaveBeenLastCalledWith({
      page: 1,
      pageSize: 25,
      sortColumn: 'name',
      sortDirection: 'ASC',
      searchText: undefined,
    });
  });

  it('requests the next page and a new page size from the server', async () => {
    const fetchPage = renderGrid();
    await screen.findByText('1–25 of 60');

    await userEvent.click(screen.getByRole('button', { name: /next page/i }));
    expect(await screen.findByText('26–50 of 60')).toBeInTheDocument();
    expect(fetchPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, pageSize: 25 }));

    fireEvent.mouseDown(screen.getByRole('combobox'));
    await userEvent.click(within(await screen.findByRole('listbox')).getByRole('option', { name: '50' }));

    expect(await screen.findByText('1–50 of 60')).toBeInTheDocument();
    expect(fetchPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, pageSize: 50 }));
  });

  it('debounces the search and starts again at page 1', async () => {
    const fetchPage = renderGrid();
    await screen.findByText('1–25 of 60');
    await userEvent.click(screen.getByRole('button', { name: /next page/i }));
    await screen.findByText('26–50 of 60');
    fetchPage.mockClear();

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search roles' }), 'adm');

    await vi.waitFor(() =>
      expect(fetchPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, searchText: 'adm' })),
    );
    // One request for the final text, none per keystroke.
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('shows the API error message when loading fails', async () => {
    renderGrid(vi.fn(() => Promise.reject(new Error('Server unavailable'))));

    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
