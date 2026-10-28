import Typography from '@mui/material/Typography';
import { AppButton } from '../../../components/common/AppButton';
import { AppDialog } from '../../../components/common/AppDialog';
import { useIdleTimeout } from '../hooks/useIdleTimeout';

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** Warning before the idle timeout, with a live countdown. Mounted once in App. */
export function IdleTimeoutDialog() {
  const { warningOpen, secondsLeft, extending, staySignedIn, signOut } = useIdleTimeout();

  return (
    <AppDialog
      open={warningOpen}
      title="Are you still there?"
      maxWidth="xs"
      actions={
        <>
          <AppButton variant="secondary" onClick={signOut} disabled={extending}>
            Sign out
          </AppButton>
          <AppButton onClick={() => void staySignedIn()} loading={extending} autoFocus>
            Stay signed in
          </AppButton>
        </>
      }
    >
      <Typography variant="body1">
        You have been inactive for a while. For your security you will be signed out in{' '}
        <Typography component="strong" variant="body1" sx={{ fontWeight: 600 }} aria-live="polite">
          {formatCountdown(secondsLeft)}
        </Typography>
        .
      </Typography>
    </AppDialog>
  );
}
