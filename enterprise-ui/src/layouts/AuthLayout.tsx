import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { Outlet } from 'react-router';
import { surfaceColor } from '../theme/palette';
import { env } from '../utils/env';

/** Centred card on a light background with the app name; no navigation. */
export function AuthLayout() {
  return (
    <Box
      component="main"
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: surfaceColor,
        px: 2,
        py: 4,
      }}
    >
      <Box sx={{ width: '100%', maxWidth: 420 }}>
        <Typography variant="h5" component="p" color="primary" sx={{ textAlign: 'center', mb: 3 }}>
          {env.appName}
        </Typography>
        <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 }, boxShadow: '0 1px 3px rgba(31, 41, 51, 0.08)' }}>
          <Outlet />
        </Paper>
      </Box>
    </Box>
  );
}
