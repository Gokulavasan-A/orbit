import AccountCircleOutlined from '@mui/icons-material/AccountCircleOutlined';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { RoutePaths } from '../../constants/routes';
import { logout, selectCurrentUser } from '../../features/auth/authSlice';
import { userDisplayName } from '../../utils/userDisplayName';

/** Top-bar user menu: Change password and Sign out. */
export function UserMenu() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const user = useAppSelector(selectCurrentUser);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  if (!user) {
    return null;
  }
  const close = () => setAnchor(null);

  return (
    <>
      <Button
        color="inherit"
        startIcon={<AccountCircleOutlined />}
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-haspopup="menu"
        aria-expanded={anchor ? 'true' : undefined}
        aria-controls={anchor ? 'user-menu' : undefined}
        sx={{ color: 'text.primary' }}
      >
        {userDisplayName(user)}
      </Button>
      <Menu
        id="user-menu"
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem disabled sx={{ opacity: '1 !important' }}>
          <ListItemText primary={user.username} secondary={user.email} />
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            close();
            void navigate(RoutePaths.changePassword);
          }}
        >
          Change password
        </MenuItem>
        <MenuItem
          onClick={() => {
            close();
            void dispatch(logout());
          }}
        >
          Sign out
        </MenuItem>
      </Menu>
    </>
  );
}
