import TextField, { type TextFieldProps } from '@mui/material/TextField';
import { useField } from 'formik';

export type AppTextFieldProps = Omit<TextFieldProps, 'name' | 'value' | 'error'> & {
  /** Formik field name; value, change, blur and the error message are wired from it. */
  name: string;
};

/**
 * Text input bound to the surrounding Formik form (AppForm). Shows the field's error once the
 * field was touched, or right away for server field errors.
 */
export function AppTextField({ name, helperText, ...rest }: AppTextFieldProps) {
  const [field, meta] = useField<string>(name);
  const showError = Boolean(meta.touched && meta.error);
  return (
    <TextField
      {...field}
      value={field.value}
      id={rest.id ?? name}
      {...rest}
      error={showError}
      helperText={showError ? meta.error : helperText}
    />
  );
}
