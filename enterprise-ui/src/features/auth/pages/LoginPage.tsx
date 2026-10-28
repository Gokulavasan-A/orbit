import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import { Navigate, useLocation, useNavigate } from 'react-router';
import * as Yup from 'yup';
import { ErrorCodes, isApiErrorData } from '../../../api/apiError';
import { useAppDispatch, useAppSelector } from '../../../app/hooks';
import { AppForm } from '../../../components/common/AppForm';
import { AppPasswordField } from '../../../components/common/AppPasswordField';
import { AppTextField } from '../../../components/common/AppTextField';
import { RoutePaths } from '../../../constants/routes';
import type { LoginLocationState } from '../../../routes/ProtectedRoute';
import type { SessionEndReason } from '../../../types/auth';
import { env } from '../../../utils/env';
import { login, selectIsAuthenticated, selectSessionEndReason } from '../authSlice';

interface LoginValues {
  username: string;
  password: string;
}

const initialValues: LoginValues = { username: '', password: '' };

const schema = Yup.object({
  username: Yup.string().trim().required('Enter your username or email.').max(256),
  password: Yup.string().required('Enter your password.').max(128),
});

const sessionMessages: Record<SessionEndReason, { severity: 'info' | 'warning'; text: string }> = {
  expired: { severity: 'warning', text: 'Your session has expired. Please sign in again.' },
  revoked: { severity: 'warning', text: 'Your session has ended. Please sign in again.' },
  idle: {
    severity: 'info',
    text: `You were signed out after ${Math.round(env.idleTimeoutMs / 60_000)} minutes of inactivity.`,
  },
  signedOut: { severity: 'info', text: 'You have been signed out.' },
};

/**
 * The API's messages are shown as they are: INVALID_CREDENTIALS is deliberately generic, and
 * ACCOUNT_LOCKED carries no remaining time. A 429 adds the Retry-After wait.
 */
function loginErrorMessage(error: unknown): string | null {
  if (!isApiErrorData(error)) {
    return 'Sign-in failed. Please try again.';
  }
  if (error.code === ErrorCodes.RATE_LIMITED) {
    return error.retryAfterSeconds
      ? `Too many sign-in attempts. Please try again in ${error.retryAfterSeconds} seconds.`
      : 'Too many sign-in attempts. Please wait and try again.';
  }
  if (error.code === ErrorCodes.VALIDATION_FAILED && error.fieldErrors.length > 0) {
    return null;
  }
  return error.message;
}

export default function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const authenticated = useAppSelector(selectIsAuthenticated);
  const sessionEndReason = useAppSelector(selectSessionEndReason);
  const from = (location.state as LoginLocationState | null)?.from ?? RoutePaths.home;

  if (authenticated) {
    return <Navigate to={from} replace />;
  }
  const notice = sessionEndReason ? sessionMessages[sessionEndReason] : null;

  return (
    <>
      <Typography variant="pageTitle" sx={{ mb: 0.5 }}>
        Sign in
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        Use your username or email address.
      </Typography>
      <AppForm<LoginValues>
        ariaLabel="Sign in"
        initialValues={initialValues}
        validationSchema={schema}
        submitLabel="Sign in"
        fullWidthSubmit
        getErrorMessage={loginErrorMessage}
        header={notice && <Alert severity={notice.severity}>{notice.text}</Alert>}
        onSubmit={async (values) => {
          const auth = await dispatch(
            login({ username: values.username.trim(), password: values.password }),
          ).unwrap();
          void navigate(auth.mustChangePassword ? RoutePaths.changePassword : from, { replace: true });
        }}
      >
        <AppTextField name="username" label="Username or email" autoComplete="username" autoFocus />
        <AppPasswordField name="password" label="Password" autoComplete="current-password" />
      </AppForm>
    </>
  );
}
