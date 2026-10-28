import Button, { type ButtonProps } from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';

export type AppButtonVariant = 'primary' | 'secondary' | 'text' | 'danger';

export interface AppButtonProps extends Omit<ButtonProps, 'variant' | 'color'> {
  /**
   * - `primary`: filled blue, the main action of a screen or dialog (one per area)
   * - `secondary`: outlined, neutral actions such as Cancel
   * - `text`: low-emphasis actions such as links in a toolbar
   * - `danger`: filled red, destructive actions (always behind a ConfirmDialog)
   * @default 'primary'
   */
  variant?: AppButtonVariant;
  /** Shows a spinner, disables the button and sets aria-busy. */
  loading?: boolean;
}

const variantProps: Record<AppButtonVariant, Pick<ButtonProps, 'variant' | 'color'>> = {
  primary: { variant: 'contained', color: 'primary' },
  secondary: { variant: 'outlined', color: 'secondary' },
  text: { variant: 'text', color: 'primary' },
  danger: { variant: 'contained', color: 'error' },
};

/** The only button screens use. */
export function AppButton({
  variant = 'primary',
  loading = false,
  disabled,
  startIcon,
  children,
  ...rest
}: AppButtonProps) {
  return (
    <Button
      {...variantProps[variant]}
      {...rest}
      disabled={disabled === true || loading}
      aria-busy={loading || undefined}
      startIcon={loading ? <CircularProgress size={16} color="inherit" aria-label="Loading" /> : startIcon}
    >
      {children}
    </Button>
  );
}
