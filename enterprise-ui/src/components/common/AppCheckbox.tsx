import Checkbox, { type CheckboxProps } from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import { useField } from 'formik';

export interface AppCheckboxProps extends Omit<CheckboxProps, 'name' | 'checked' | 'onChange'> {
  /** Formik field name of a boolean value. */
  name: string;
  label: string;
  helperText?: string;
}

/** Checkbox bound to a boolean field of the surrounding Formik form. */
export function AppCheckbox({ name, label, helperText, ...rest }: AppCheckboxProps) {
  const [field, meta] = useField<boolean>({ name, type: 'checkbox' });
  const showError = Boolean(meta.touched && meta.error);
  return (
    <FormControl error={showError}>
      <FormControlLabel
        label={label}
        control={
          <Checkbox {...rest} name={field.name} checked={Boolean(field.checked)} onChange={field.onChange} />
        }
      />
      {(showError || helperText) && <FormHelperText>{showError ? meta.error : helperText}</FormHelperText>}
    </FormControl>
  );
}
