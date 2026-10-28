import type { TypographyVariantsOptions } from '@mui/material/styles';

/**
 * The single place for font families, sizes and weights. Screens never set font sizes
 * directly; they pick a variant, and override locally only via `sx` or a local styled
 * component. The custom variants are typed in `augmentation.d.ts`.
 */
export const fontFamily = '"Inter Variable", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

export const fontWeights = {
  regular: 400,
  medium: 500,
  semibold: 600,
  bold: 700,
} as const;

export const typography: TypographyVariantsOptions = {
  fontFamily,
  fontSize: 14,
  htmlFontSize: 16,
  fontWeightRegular: fontWeights.regular,
  fontWeightMedium: fontWeights.medium,
  fontWeightBold: fontWeights.bold,

  h1: { fontSize: '2rem', fontWeight: fontWeights.semibold, lineHeight: 1.25 },
  h2: { fontSize: '1.75rem', fontWeight: fontWeights.semibold, lineHeight: 1.3 },
  h3: { fontSize: '1.5rem', fontWeight: fontWeights.semibold, lineHeight: 1.3 },
  h4: { fontSize: '1.25rem', fontWeight: fontWeights.semibold, lineHeight: 1.35 },
  h5: { fontSize: '1.125rem', fontWeight: fontWeights.semibold, lineHeight: 1.4 },
  h6: { fontSize: '1rem', fontWeight: fontWeights.semibold, lineHeight: 1.4 },
  subtitle1: { fontSize: '1rem', fontWeight: fontWeights.medium, lineHeight: 1.5 },
  subtitle2: { fontSize: '0.875rem', fontWeight: fontWeights.medium, lineHeight: 1.5 },
  body1: { fontSize: '0.875rem', fontWeight: fontWeights.regular, lineHeight: 1.55 },
  body2: { fontSize: '0.8125rem', fontWeight: fontWeights.regular, lineHeight: 1.5 },
  caption: { fontSize: '0.75rem', fontWeight: fontWeights.regular, lineHeight: 1.45 },
  button: { fontSize: '0.875rem', fontWeight: fontWeights.medium, textTransform: 'none' },
  overline: { fontSize: '0.6875rem', fontWeight: fontWeights.semibold, letterSpacing: '0.06em' },

  // Custom variants
  pageTitle: { fontSize: '1.375rem', fontWeight: fontWeights.semibold, lineHeight: 1.3 },
  sectionTitle: { fontSize: '1rem', fontWeight: fontWeights.semibold, lineHeight: 1.4 },
  label: { fontSize: '0.8125rem', fontWeight: fontWeights.medium, lineHeight: 1.4 },
  helperText: { fontSize: '0.75rem', fontWeight: fontWeights.regular, lineHeight: 1.4 },
};

/** Base font size in px, shared with the AG Grid theme. */
export const baseFontSizePx = 14;
