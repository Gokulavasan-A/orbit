import { useRouteError } from 'react-router';
import { StatusPage } from './StatusPage';

/** Route error element, e.g. when a lazy page chunk fails to load after a deployment. */
export function RouteErrorPage() {
  const error = useRouteError();
  console.error('Route error', error);
  return (
    <StatusPage
      code="Error"
      title="This page could not be loaded"
      message="Reload the page. If the problem persists, contact support."
    />
  );
}
