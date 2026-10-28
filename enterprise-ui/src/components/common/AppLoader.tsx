import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';

export interface AppLoaderProps {
  /**
   * - `page`: fills the viewport (app start, route loading)
   * - `inline`: a small spinner inside a section
   * @default 'inline'
   */
  variant?: 'page' | 'inline';
  /** Text under the spinner; also the accessible label. @default 'Loading…' */
  label?: string;
}

/** Loading indicator. */
export function AppLoader({ variant = 'inline', label = 'Loading…' }: AppLoaderProps) {
  const page = variant === 'page';
  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 1.5,
        py: page ? 0 : 3,
        minHeight: page ? '100vh' : undefined,
      }}
    >
      <CircularProgress size={page ? 36 : 24} aria-label={label} />
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}
