import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { dismissToast, selectToasts } from '../../features/ui/uiSlice';

/** Auto-hide delay in ms; errors stay longer. */
const AUTO_HIDE_MS = { default: 5000, error: 8000 } as const;

/**
 * Shows the toasts queued in uiSlice, one at a time. Mounted once in App. Trigger a toast with
 * `dispatch(showToast(message, severity))`; never render a Snackbar in a screen.
 */
export function AppToast() {
  const dispatch = useAppDispatch();
  const toast = useAppSelector(selectToasts)[0];

  if (!toast) {
    return null;
  }
  const close = () => dispatch(dismissToast(toast.id));
  return (
    <Snackbar
      key={toast.id}
      open
      autoHideDuration={toast.severity === 'error' ? AUTO_HIDE_MS.error : AUTO_HIDE_MS.default}
      onClose={(_, reason) => {
        if (reason !== 'clickaway') {
          close();
        }
      }}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
    >
      <Alert severity={toast.severity} variant="standard" onClose={close} sx={{ minWidth: 320 }}>
        {toast.message}
      </Alert>
    </Snackbar>
  );
}
