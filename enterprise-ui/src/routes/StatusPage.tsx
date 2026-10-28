import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router';
import { AppButton } from '../components/common/AppButton';
import { RoutePaths } from '../constants/routes';

export interface StatusPageProps {
  code: string;
  title: string;
  message: string;
}

/** Shared body of the 403, 404 and route error pages. */
export function StatusPage({ code, title, message }: StatusPageProps) {
  const navigate = useNavigate();
  return (
    <Box sx={{ py: 8, px: 2, textAlign: 'center' }}>
      <Typography variant="overline" color="text.secondary">
        {code}
      </Typography>
      <Typography variant="pageTitle" sx={{ display: 'block', mt: 1 }}>
        {title}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mt: 1, mb: 3 }}>
        {message}
      </Typography>
      <AppButton onClick={() => void navigate(RoutePaths.home)}>Go to Home</AppButton>
    </Box>
  );
}
