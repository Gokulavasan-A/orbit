import Dialog, { type DialogProps } from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import { useId, type ReactNode } from 'react';

export interface AppDialogProps extends Omit<DialogProps, 'title' | 'onClose'> {
  open: boolean;
  title: ReactNode;
  /** Buttons at the bottom (AppButton). */
  actions?: ReactNode;
  /** Close by Escape or backdrop click. Omit to make the dialog modal-only (no implicit close). */
  onClose?: () => void;
  children: ReactNode;
}

/** Standard dialog: title, content and an action row. */
export function AppDialog({
  open,
  title,
  actions,
  onClose,
  children,
  maxWidth = 'sm',
  fullWidth = true,
  ...rest
}: AppDialogProps) {
  const titleId = useId();
  return (
    <Dialog
      {...rest}
      open={open}
      onClose={onClose ? () => onClose() : undefined}
      maxWidth={maxWidth}
      fullWidth={fullWidth}
      aria-labelledby={titleId}
    >
      <DialogTitle id={titleId} variant="sectionTitle">
        {title}
      </DialogTitle>
      <DialogContent>{children}</DialogContent>
      {actions && <DialogActions sx={{ px: 3, pb: 2 }}>{actions}</DialogActions>}
    </Dialog>
  );
}
