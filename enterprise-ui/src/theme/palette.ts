import type { PaletteOptions } from '@mui/material/styles';

/**
 * Light, neutral palette: white background, light grey surfaces, one soft blue primary and
 * muted status colours. No neon or saturated colours. Add new colours here, never inline in
 * screens.
 */
export const palette: PaletteOptions = {
  mode: 'light',
  primary: { main: '#3B6FB6', light: '#6C93CB', dark: '#2C5591', contrastText: '#FFFFFF' },
  secondary: { main: '#5F6B7A', light: '#8A94A1', dark: '#454F5C', contrastText: '#FFFFFF' },
  success: { main: '#3C8D5A', light: '#E7F3EB', dark: '#2D6B44', contrastText: '#FFFFFF' },
  warning: { main: '#C08A2B', light: '#FBF1DF', dark: '#8F6620', contrastText: '#FFFFFF' },
  error: { main: '#C2453D', light: '#F9E5E3', dark: '#93332D', contrastText: '#FFFFFF' },
  info: { main: '#3B82B6', light: '#E4EFF8', dark: '#2C6189', contrastText: '#FFFFFF' },
  text: { primary: '#1F2933', secondary: '#52606D', disabled: '#9AA5B1' },
  divider: '#E3E8EE',
  background: { default: '#FFFFFF', paper: '#FFFFFF' },
  grey: {
    50: '#F6F8FA',
    100: '#EEF1F5',
    200: '#E3E8EE',
    300: '#CBD2D9',
    400: '#9AA5B1',
    500: '#7B8794',
    600: '#616E7C',
    700: '#52606D',
    800: '#3E4C59',
    900: '#1F2933',
  },
  action: {
    hover: 'rgba(59, 111, 182, 0.06)',
    selected: 'rgba(59, 111, 182, 0.10)',
  },
};

/** Light grey surface used behind cards (AuthLayout, content area, grid headers). */
export const surfaceColor = '#F6F8FA';
