import MenuIcon from '@mui/icons-material/Menu';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { useNavigation } from 'react-router';
import { useAppSelector } from '../../app/hooks';
import { selectIsGlobalLoading } from '../../features/ui/uiSlice';
import { env } from '../../utils/env';
import { UserMenu } from './UserMenu';

/** Top bar: navigation toggle (small screens), app name, user menu and a thin progress bar. */
export function TopBar({ onToggleNav }: { onToggleNav: () => void }) {
  const routeLoading = useNavigation().state === 'loading';
  const requestLoading = useAppSelector(selectIsGlobalLoading);

  return (
    <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
      <Toolbar variant="dense" sx={{ minHeight: 56 }}>
        <IconButton
          edge="start"
          onClick={onToggleNav}
          aria-label="Open navigation"
          sx={{ mr: 1, display: { md: 'none' } }}
        >
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" component="p" color="primary" noWrap>
          {env.appName}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        <UserMenu />
      </Toolbar>
      {(routeLoading || requestLoading) && (
        <LinearProgress sx={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 2 }} />
      )}
    </AppBar>
  );
}
