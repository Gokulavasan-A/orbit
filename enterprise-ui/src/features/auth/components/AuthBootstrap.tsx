import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState, type ReactNode } from 'react';
import { authService } from '../../../api/authService';
import { useAppDispatch, useAppSelector } from '../../../app/hooks';
import { AppButton } from '../../../components/common/AppButton';
import { AppLoader } from '../../../components/common/AppLoader';
import { bootstrapCompleted, loadProfile, selectAuthStatus } from '../authSlice';

/**
 * Restores the session on app start: GET /csrf, then a silent POST /refresh from the refresh
 * cookie, behind a full-page loader. A restored session is then checked with GET /me, which also
 * resets the server's idle timer, so the UI idle timeout starts from a known point.
 *
 * StrictMode runs this effect twice in dev: `authService.bootstrap()` returns the same promise to
 * both runs (the refresh cookie is sent once), and only the surviving run continues.
 */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  const dispatch = useAppDispatch();
  const status = useAppSelector(selectAuthStatus);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    authService.bootstrap().then(
      (auth) => {
        if (!active) {
          return;
        }
        if (auth) {
          // The tokens were stored by the client's onRefreshed hook.
          void dispatch(loadProfile());
        } else {
          dispatch(bootstrapCompleted());
        }
      },
      () => {
        if (active) {
          setFailed(true);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [dispatch, attempt]);

  if (failed) {
    return (
      <Box role="alert" sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
        <Box sx={{ textAlign: 'center' }}>
          <Typography variant="pageTitle">Cannot reach the server</Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 1, mb: 3 }}>
            Check your connection and try again.
          </Typography>
          <AppButton
            onClick={() => {
              authService.resetBootstrap();
              setFailed(false);
              setAttempt((a) => a + 1);
            }}
          >
            Try again
          </AppButton>
        </Box>
      </Box>
    );
  }

  if (status === 'bootstrapping') {
    return <AppLoader variant="page" label="Loading…" />;
  }
  return children;
}
