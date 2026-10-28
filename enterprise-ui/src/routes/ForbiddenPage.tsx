import { StatusPage } from './StatusPage';

export default function ForbiddenPage() {
  return (
    <StatusPage
      code="403"
      title="Access denied"
      message="You do not have permission to view this page. Ask an administrator if you need access."
    />
  );
}
