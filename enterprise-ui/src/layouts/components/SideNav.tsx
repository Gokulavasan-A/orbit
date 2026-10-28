import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import { NavLink, useLocation } from 'react-router';
import { useAppSelector } from '../../app/hooks';
import { selectPermissions } from '../../features/auth/authSlice';
import { visibleMenuItems } from '../../routes/menuConfig';

/** Left navigation built from menuConfig, filtered by the user's permissions. */
export function SideNav({ onNavigate }: { onNavigate?: () => void }) {
  const permissions = useAppSelector(selectPermissions);
  const { pathname } = useLocation();

  return (
    <List component="nav" aria-label="Main navigation" sx={{ px: 1.5, py: 2 }}>
      {visibleMenuItems(permissions).map(({ id, label, path, icon: Icon }) => {
        const selected = path === '/' ? pathname === '/' : pathname.startsWith(path);
        return (
          <ListItemButton
            key={id}
            component={NavLink}
            to={path}
            selected={selected}
            onClick={onNavigate}
            sx={{ mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 36, color: selected ? 'primary.main' : 'text.secondary' }}>
              <Icon fontSize="small" />
            </ListItemIcon>
            <ListItemText primary={label} slotProps={{ primary: { variant: 'subtitle2' } }} />
          </ListItemButton>
        );
      })}
    </List>
  );
}
