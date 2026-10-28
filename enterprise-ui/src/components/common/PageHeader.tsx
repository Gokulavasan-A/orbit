import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

export interface PageHeaderProps {
  /** Page title (the page's only h1). */
  title: string;
  subtitle?: ReactNode;
  /** Buttons on the right (AppButton). */
  actions?: ReactNode;
}

/** Title row at the top of every screen in MainLayout. */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ mb: 3, alignItems: { sm: 'flex-start' }, justifyContent: 'space-between' }}
    >
      <Box>
        <Typography variant="pageTitle">{title}</Typography>
        {subtitle && (
          <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {actions && (
        <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          {actions}
        </Stack>
      )}
    </Stack>
  );
}
