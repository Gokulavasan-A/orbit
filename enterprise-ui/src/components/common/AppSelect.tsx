import MenuItem from '@mui/material/MenuItem';
import TextField, { type TextFieldProps } from '@mui/material/TextField';
import { useField } from 'formik';

export interface AppSelectOption<V extends string | number = string> {
  value: V;
  label: string;
  disabled?: boolean;
}

export type AppSelectProps<V extends string | number = string> = Omit<
  TextFieldProps,
  'name' | 'value' | 'error' | 'select' | 'children'
> & {
  /** Formik field name. */
  name: string;
  options: AppSelectOption<V>[];
  /** Adds an empty first option with this label (value ''). */
  placeholderOption?: string;
};

/** Single-select dropdown bound to the surrounding Formik form. */
export function AppSelect<V extends string | number = string>({
  name,
  options,
  placeholderOption,
  helperText,
  ...rest
}: AppSelectProps<V>) {
  const [field, meta] = useField<V | ''>(name);
  const showError = Boolean(meta.touched && meta.error);
  return (
    <TextField
      {...field}
      id={rest.id ?? name}
      {...rest}
      select
      error={showError}
      helperText={showError ? meta.error : helperText}
    >
      {placeholderOption !== undefined && <MenuItem value="">{placeholderOption}</MenuItem>}
      {options.map((option) => (
        <MenuItem key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </MenuItem>
      ))}
    </TextField>
  );
}
