import type { CSSProperties } from 'react';

// Custom typography variants defined in typography.ts.
declare module '@mui/material/styles' {
  interface TypographyVariants {
    pageTitle: CSSProperties;
    sectionTitle: CSSProperties;
    label: CSSProperties;
    helperText: CSSProperties;
  }

  interface TypographyVariantsOptions {
    pageTitle?: CSSProperties;
    sectionTitle?: CSSProperties;
    label?: CSSProperties;
    helperText?: CSSProperties;
  }
}

declare module '@mui/material/Typography' {
  interface TypographyPropsVariantOverrides {
    pageTitle: true;
    sectionTitle: true;
    label: true;
    helperText: true;
  }
}
