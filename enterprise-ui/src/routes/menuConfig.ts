import AdminPanelSettingsOutlined from '@mui/icons-material/AdminPanelSettingsOutlined';
import HomeOutlined from '@mui/icons-material/HomeOutlined';
import type { SvgIconComponent } from '@mui/icons-material';
import { Permissions } from '../constants/permissions';
import { RoutePaths } from '../constants/routes';

export interface MenuItem {
  id: string;
  label: string;
  path: string;
  icon: SvgIconComponent;
  /** Shown only with this permission. Must match the route's PermissionRoute. */
  permission?: string;
  /** Short text for the module links on the home page. */
  description?: string;
}

/** Left navigation, in display order. Filtered by the user's permissions in SideNav and Home. */
export const menuItems: MenuItem[] = [
  { id: 'home', label: 'Home', path: RoutePaths.home, icon: HomeOutlined },
  {
    id: 'roles',
    label: 'Roles',
    path: RoutePaths.roles,
    icon: AdminPanelSettingsOutlined,
    permission: Permissions.ROLE_VIEW,
    description: 'Roles and the permissions they grant',
  },
];

export function visibleMenuItems(permissions: readonly string[]): MenuItem[] {
  return menuItems.filter((item) => !item.permission || permissions.includes(item.permission));
}
