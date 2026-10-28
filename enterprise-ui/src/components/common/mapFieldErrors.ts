import type { FormikErrors, FormikTouched } from 'formik';
import { isApiErrorData } from '../../api/apiError';

/**
 * Turns the API's `fieldErrors` (from an ApiError or its serialized form) into Formik errors and
 * touched flags. Several messages for one field are joined. Fields not in the form are returned
 * in `unmatched` so the form can show them in its error alert instead.
 */
export function mapFieldErrors<T extends object>(
  error: unknown,
  values: T,
): { errors: FormikErrors<T>; touched: FormikTouched<T>; unmatched: string[] } {
  const errors: Record<string, string> = {};
  const touched: Record<string, boolean> = {};
  const unmatched: string[] = [];

  if (isApiErrorData(error)) {
    for (const { field, message } of error.fieldErrors) {
      if (field in values) {
        errors[field] = errors[field] ? `${errors[field]}; ${message}` : capitalize(message);
        touched[field] = true;
      } else {
        unmatched.push(`${field}: ${message}`);
      }
    }
  }
  return { errors: errors as FormikErrors<T>, touched: touched as FormikTouched<T>, unmatched };
}

function capitalize(message: string): string {
  return message.charAt(0).toUpperCase() + message.slice(1);
}
