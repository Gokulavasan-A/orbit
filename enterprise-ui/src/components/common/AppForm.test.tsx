import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import * as Yup from 'yup';
import { ApiError } from '../../api/apiError';
import { renderWithProviders } from '../../test/renderWithProviders';
import { AppForm } from './AppForm';
import { AppPasswordField } from './AppPasswordField';
import { AppTextField } from './AppTextField';

interface Values {
  username: string;
  newPassword: string;
}

const validationError = new ApiError({
  status: 400,
  code: 'VALIDATION_FAILED',
  message: 'One or more fields are invalid.',
  fieldErrors: [
    { field: 'newPassword', message: 'must contain a digit' },
    { field: 'newPassword', message: 'must contain a special character' },
    { field: 'username', message: 'is already taken' },
  ],
  correlationId: 'c-1',
  retryAfterSeconds: null,
});

function renderForm(onSubmit: (values: Values) => Promise<void>) {
  return renderWithProviders(
    <AppForm<Values>
      initialValues={{ username: '', newPassword: '' }}
      validationSchema={Yup.object({ username: Yup.string().required('Required.') })}
      onSubmit={onSubmit}
      submitLabel="Submit"
    >
      <AppTextField name="username" label="Username" />
      <AppPasswordField name="newPassword" label="New password" autoComplete="new-password" />
    </AppForm>,
  );
}

describe('AppForm', () => {
  it('shows Yup errors and does not submit invalid values', async () => {
    const onSubmit = vi.fn(() => Promise.resolve());
    renderForm(onSubmit);

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('Required.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('maps server fieldErrors onto the matching Formik fields', async () => {
    renderForm(() => Promise.reject(validationError));

    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('New password'), 'weak');
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(
      await screen.findByText('Must contain a digit; must contain a special character'),
    ).toBeInTheDocument();
    expect(screen.getByText('Is already taken')).toBeInTheDocument();
    expect(screen.getByLabelText('New password')).toHaveAttribute('aria-invalid', 'true');
    // Field errors only: no general alert.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows the API message in an alert when there are no field errors', async () => {
    renderForm(() =>
      Promise.reject(
        new ApiError({
          status: 409,
          code: 'USERNAME_EXISTS',
          message: 'This username is already taken.',
          fieldErrors: [],
          correlationId: null,
          retryAfterSeconds: null,
        }),
      ),
    );

    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This username is already taken.');
  });

  it('sets the autocomplete attribute on password fields', () => {
    renderForm(() => Promise.resolve());
    expect(screen.getByLabelText('New password')).toHaveAttribute('autocomplete', 'new-password');
  });
});
