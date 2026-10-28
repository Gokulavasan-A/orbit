import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useAppDispatch, useAppSelector } from '../../../app/hooks';
import { AppForm } from '../../../components/common/AppForm';
import { AppPasswordField } from '../../../components/common/AppPasswordField';
import { RoutePaths } from '../../../constants/routes';
import { showToast } from '../../ui/uiSlice';
import { changePassword, logout, selectCurrentUser, selectMustChangePassword } from '../authSlice';
import { changePasswordSchema, passwordPolicyHints } from '../passwordPolicy';

interface ChangePasswordValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const initialValues: ChangePasswordValues = { currentPassword: '', newPassword: '', confirmPassword: '' };

/**
 * Forced (mustChangePassword: every other route redirects here, no Cancel) or voluntary (from the
 * user menu). The API's field errors (policy violations on newPassword, wrong currentPassword)
 * appear on the fields.
 */
export default function ChangePasswordPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const user = useAppSelector(selectCurrentUser);
  const forced = useAppSelector(selectMustChangePassword);
  const schema = useMemo(() => changePasswordSchema(user?.username), [user?.username]);

  return (
    <>
      <Typography variant="pageTitle" sx={{ mb: 0.5 }}>
        Change password
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        Your other sessions are signed out when the password changes.
      </Typography>
      <AppForm<ChangePasswordValues>
        ariaLabel="Change password"
        initialValues={initialValues}
        validationSchema={schema}
        submitLabel="Change password"
        onCancel={forced ? undefined : () => void navigate(-1)}
        header={
          forced && <Alert severity="info">You must change your password before you can continue.</Alert>
        }
        onSubmit={async (values) => {
          await dispatch(changePassword(values)).unwrap();
          dispatch(showToast('Your password has been changed.', 'success'));
          void navigate(RoutePaths.home, { replace: true });
        }}
      >
        <AppPasswordField name="currentPassword" label="Current password" autoComplete="current-password" />
        <AppPasswordField name="newPassword" label="New password" autoComplete="new-password" />
        <Box
          component="ul"
          sx={{ m: 0, pl: 2.5, color: 'text.secondary' }}
          aria-label="Password requirements"
        >
          {passwordPolicyHints.map((hint) => (
            <Typography component="li" variant="helperText" key={hint}>
              {hint}
            </Typography>
          ))}
        </Box>
        <AppPasswordField name="confirmPassword" label="Confirm new password" autoComplete="new-password" />
      </AppForm>
      {forced && (
        <Typography variant="body2" sx={{ mt: 2, textAlign: 'center' }}>
          <Link component="button" type="button" onClick={() => void dispatch(logout())}>
            Sign out
          </Link>
        </Typography>
      )}
    </>
  );
}
