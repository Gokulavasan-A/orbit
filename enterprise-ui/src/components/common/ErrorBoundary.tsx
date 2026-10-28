import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AppButton } from './AppButton';

export interface ErrorBoundaryProps {
  children: ReactNode;
  /** Custom fallback; the default offers a reload. */
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/**
 * Catches render errors below it and shows a recoverable message instead of a blank page.
 * Logs only the error itself; never log state (it holds the access token).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled UI error', error, info.componentStack);
  }

  override render() {
    if (!this.state.hasError) {
      return this.props.children;
    }
    return (
      this.props.fallback ?? (
        <Box role="alert" sx={{ p: 6, textAlign: 'center' }}>
          <Typography variant="pageTitle">Something went wrong</Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 1, mb: 3 }}>
            An unexpected error occurred. Reload the page to continue.
          </Typography>
          <AppButton onClick={() => window.location.reload()}>Reload</AppButton>
        </Box>
      )
    );
  }
}
