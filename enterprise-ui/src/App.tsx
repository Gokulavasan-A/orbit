import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import type { AppStore } from './app/store';
import { AppToast } from './components/common/AppToast';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import { AuthBootstrap } from './features/auth/components/AuthBootstrap';
import { IdleTimeoutDialog } from './features/auth/components/IdleTimeoutDialog';
import { AppRoutes } from './routes/AppRoutes';
import { theme } from './theme/theme';

export function App({ store }: { store: AppStore }) {
  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <ErrorBoundary>
          <AuthBootstrap>
            <AppRoutes />
            <IdleTimeoutDialog />
          </AuthBootstrap>
          <AppToast />
        </ErrorBoundary>
      </ThemeProvider>
    </Provider>
  );
}
