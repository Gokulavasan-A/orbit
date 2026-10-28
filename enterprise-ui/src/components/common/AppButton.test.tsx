import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { AppButton } from './AppButton';

describe('AppButton', () => {
  it('renders the variants with the mapped MUI styles', () => {
    renderWithProviders(
      <>
        <AppButton>Save</AppButton>
        <AppButton variant="secondary">Cancel</AppButton>
        <AppButton variant="text">More</AppButton>
        <AppButton variant="danger">Delete</AppButton>
      </>,
    );

    expect(screen.getByRole('button', { name: 'Save' })).toHaveClass(
      'MuiButton-contained',
      'MuiButton-colorPrimary',
    );
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveClass('MuiButton-outlined');
    expect(screen.getByRole('button', { name: 'More' })).toHaveClass('MuiButton-text');
    expect(screen.getByRole('button', { name: 'Delete' })).toHaveClass('MuiButton-colorError');
  });

  it('calls onClick', async () => {
    const onClick = vi.fn();
    renderWithProviders(<AppButton onClick={onClick}>Save</AppButton>);

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(onClick).toHaveBeenCalledOnce();
  });

  it('is disabled, busy and shows a spinner while loading', async () => {
    const onClick = vi.fn();
    renderWithProviders(
      <AppButton loading onClick={onClick}>
        Save
      </AppButton>,
    );

    const button = screen.getByRole('button', { name: /save/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('progressbar', { name: 'Loading' })).toBeInTheDocument();
    await userEvent.click(button).catch(() => undefined);
    expect(onClick).not.toHaveBeenCalled();
  });
});
