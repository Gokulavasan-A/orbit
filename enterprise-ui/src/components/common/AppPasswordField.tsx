import Visibility from '@mui/icons-material/VisibilityOutlined';
import VisibilityOff from '@mui/icons-material/VisibilityOffOutlined';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import { useState } from 'react';
import { AppTextField, type AppTextFieldProps } from './AppTextField';

export type AppPasswordFieldProps = Omit<AppTextFieldProps, 'type' | 'autoComplete'> & {
  /**
   * Required so password managers behave: `current-password` for an existing password,
   * `new-password` for a new one and its confirmation.
   */
  autoComplete: 'current-password' | 'new-password';
};

/** Password input bound to Formik, with a show/hide toggle. */
export function AppPasswordField({ autoComplete, slotProps, ...rest }: AppPasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  return (
    <AppTextField
      {...rest}
      type={visible ? 'text' : 'password'}
      autoComplete={autoComplete}
      slotProps={{
        ...slotProps,
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? 'Hide password' : 'Show password'}
                onClick={() => setVisible((v) => !v)}
                onMouseDown={(e) => e.preventDefault()}
                edge="end"
              >
                {visible ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}
