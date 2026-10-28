import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { AppButton } from './AppButton';
import { AppDialog } from './AppDialog';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: ReactNode;
  /** @default 'Confirm' */
  confirmLabel?: string;
  /** @default 'Cancel' */
  cancelLabel?: string;
  /** Red confirm button for destructive actions. */
  danger?: boolean;
  /** Spinner on the confirm button while the action runs. */
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Yes/no question before an action. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <AppDialog
      open={open}
      title={title}
      maxWidth="xs"
      onClose={loading ? undefined : onCancel}
      actions={
        <>
          <AppButton variant="secondary" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </AppButton>
          <AppButton variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </AppButton>
        </>
      }
    >
      {typeof message === 'string' ? <Typography variant="body1">{message}</Typography> : message}
    </AppDialog>
  );
}
