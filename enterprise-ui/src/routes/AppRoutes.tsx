import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router';
import { AppLoader } from '../components/common/AppLoader';
import { Permissions } from '../constants/permissions';
import { RoutePaths } from '../constants/routes';
import { AuthLayout } from '../layouts/AuthLayout';
import { MainLayout } from '../layouts/MainLayout';
import { PermissionRoute } from './PermissionRoute';
import { ProtectedRoute } from './ProtectedRoute';
import { RouteErrorPage } from './RouteErrorPage';

/** Lazy page: the module's default export becomes the route component (own chunk). */
function page(load: () => Promise<{ default: React.ComponentType }>): RouteObject['lazy'] {
  return async () => ({ Component: (await load()).default });
}

/**
 * Route tree. Every page is lazy-loaded. To add a module: add its page under features/<module>,
 * a route here (inside PermissionRoute when it needs a permission) and an entry in menuConfig.ts.
 */
const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <AppLoader variant="page" />,
    children: [
      {
        element: <AuthLayout />,
        children: [{ path: RoutePaths.login, lazy: page(() => import('../features/auth/pages/LoginPage')) }],
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              {
                path: RoutePaths.changePassword,
                lazy: page(() => import('../features/auth/pages/ChangePasswordPage')),
              },
            ],
          },
          {
            element: <MainLayout />,
            children: [
              { index: true, lazy: page(() => import('../features/home/pages/HomePage')) },
              {
                element: <PermissionRoute permission={Permissions.ROLE_VIEW} />,
                children: [
                  { path: RoutePaths.roles, lazy: page(() => import('../features/roles/pages/RolesPage')) },
                ],
              },
              { path: RoutePaths.forbidden, lazy: page(() => import('./ForbiddenPage')) },
            ],
          },
        ],
      },
      { path: '*', lazy: page(() => import('./NotFoundPage')) },
    ],
  },
];

const router = createBrowserRouter(routes);

export function AppRoutes() {
  return <RouterProvider router={router} />;
}
