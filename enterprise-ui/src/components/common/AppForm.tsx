import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import { Form, Formik, type FormikHelpers, type FormikProps, type FormikValues } from 'formik';
import { useState, type ReactNode } from 'react';
import type { AnyObjectSchema } from 'yup';
import { isApiErrorData } from '../../api/apiError';
import { AppButton } from './AppButton';
import { mapFieldErrors } from './mapFieldErrors';

export interface AppFormProps<T extends FormikValues> {
  initialValues: T;
  /** Yup schema validated on blur and on submit. */
  validationSchema?: AnyObjectSchema;
  /**
   * Called with valid values. Throw (or reject) to fail: an ApiError's `fieldErrors` are put on
   * the matching fields automatically, and its message is shown above the fields.
   */
  onSubmit: (values: T, helpers: FormikHelpers<T>) => Promise<void> | void;
  /** The fields. A function receives the Formik bag. */
  children: ReactNode | ((formik: FormikProps<T>) => ReactNode);
  /** @default 'Save' */
  submitLabel?: string;
  /** Shows a Cancel button when set. */
  onCancel?: () => void;
  /** @default 'Cancel' */
  cancelLabel?: string;
  /**
   * Message for the alert above the fields when `onSubmit` fails. Return null for no alert.
   * Default: no alert when the error only has field errors, otherwise the error's message.
   */
  getErrorMessage?: (error: unknown) => string | null;
  /** Content above the fields, e.g. an info alert. */
  header?: ReactNode;
  /** Stretch the submit button to the form width (auth screens). */
  fullWidthSubmit?: boolean;
  /** Accessible name of the form. */
  ariaLabel?: string;
}

function defaultErrorMessage(error: unknown): string | null {
  if (isApiErrorData(error)) {
    return error.fieldErrors.length > 0 ? null : error.message;
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

/**
 * Formik wrapper used by every form: Yup validation, a submit button with loading state, an
 * optional Cancel button, and automatic mapping of server field errors into Formik errors.
 */
export function AppForm<T extends FormikValues>({
  initialValues,
  validationSchema,
  onSubmit,
  children,
  submitLabel = 'Save',
  onCancel,
  cancelLabel = 'Cancel',
  getErrorMessage = defaultErrorMessage,
  header,
  fullWidthSubmit = false,
  ariaLabel,
}: AppFormProps<T>) {
  const [formError, setFormError] = useState<string | null>(null);

  const handleSubmit = async (values: T, helpers: FormikHelpers<T>) => {
    setFormError(null);
    try {
      await onSubmit(values, helpers);
    } catch (error) {
      const { errors, touched, unmatched } = mapFieldErrors(error, values);
      helpers.setErrors(errors);
      void helpers.setTouched({ ...touched }, false);
      const message = getErrorMessage(error);
      setFormError([message, ...unmatched].filter(Boolean).join(' ') || null);
    }
  };

  return (
    <Formik<T>
      initialValues={initialValues}
      validationSchema={validationSchema}
      onSubmit={handleSubmit}
      validateOnChange={false}
      validateOnBlur
    >
      {(formik) => (
        <Form noValidate aria-label={ariaLabel}>
          <Stack spacing={2}>
            {header}
            {formError && (
              <Alert severity="error" role="alert">
                {formError}
              </Alert>
            )}
            {typeof children === 'function' ? children(formik) : children}
            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
              {onCancel && (
                <AppButton variant="secondary" onClick={onCancel} disabled={formik.isSubmitting}>
                  {cancelLabel}
                </AppButton>
              )}
              <AppButton type="submit" loading={formik.isSubmitting} fullWidth={fullWidthSubmit}>
                {submitLabel}
              </AppButton>
            </Stack>
          </Stack>
        </Form>
      )}
    </Formik>
  );
}
