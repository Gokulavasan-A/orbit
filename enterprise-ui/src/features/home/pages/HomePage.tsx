import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useAppDispatch, useAppSelector } from '../../../app/hooks';
import { AppButton } from '../../../components/common/AppButton';
import { ConfirmDialog } from '../../../components/common/ConfirmDialog';
import { PageHeader } from '../../../components/common/PageHeader';
import { RoutePaths } from '../../../constants/routes';
import { visibleMenuItems } from '../../../routes/menuConfig';
import { userDisplayName } from '../../../utils/userDisplayName';
import { logoutAll, selectCurrentUser, selectPermissions, selectRoles } from '../../auth/authSlice';
import { showToast } from '../../ui/uiSlice';

function ProfileRow({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" spacing={2}>
      <Typography variant="label" color="text.secondary" sx={{ width: 96, flexShrink: 0 }}>
        {label}
      </Typography>
      <Typography variant="body1" sx={{ wordBreak: 'break-word' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/** Dashboard: welcome, profile, roles and links to the modules the user may open. */
export default function HomePage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const user = useAppSelector(selectCurrentUser);
  const roles = useAppSelector(selectRoles);
  const permissions = useAppSelector(selectPermissions);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);

  if (!user) {
    return null;
  }
  const modules = visibleMenuItems(permissions).filter((item) => item.path !== RoutePaths.home);

  const signOutOthers = async () => {
    setSigningOut(true);
    try {
      const { sessionsRevoked } = await dispatch(logoutAll()).unwrap();
      dispatch(
        showToast(
          sessionsRevoked === 1
            ? 'Signed out 1 other session.'
            : `Signed out ${sessionsRevoked} other sessions.`,
          'success',
        ),
      );
    } catch {
      // Errors were shown by the client (toast) where relevant.
    } finally {
      setSigningOut(false);
      setConfirmOpen(false);
    }
  };

  return (
    <>
      <PageHeader
        title={`Welcome, ${userDisplayName(user)}`}
        subtitle="Here is an overview of your account."
      />
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="sectionTitle" sx={{ mb: 2 }}>
                Profile
              </Typography>
              <Stack spacing={1.25}>
                <ProfileRow
                  label="Name"
                  value={[user.firstName, user.lastName].filter(Boolean).join(' ') || '—'}
                />
                <ProfileRow label="Username" value={user.username} />
                <ProfileRow label="Email" value={user.email} />
              </Stack>
              <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
                <AppButton variant="secondary" onClick={() => void navigate(RoutePaths.changePassword)}>
                  Change password
                </AppButton>
                <AppButton variant="text" onClick={() => setConfirmOpen(true)}>
                  Sign out other sessions
                </AppButton>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="sectionTitle" sx={{ mb: 2 }}>
                Roles
              </Typography>
              {roles.length > 0 ? (
                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                  {roles.map((role) => (
                    <Chip key={role} label={role} variant="outlined" color="primary" />
                  ))}
                </Stack>
              ) : (
                <Typography variant="body1" color="text.secondary">
                  You have no roles assigned.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={12}>
          <Typography variant="sectionTitle" sx={{ mt: 1, mb: 1.5 }}>
            Modules
          </Typography>
          {modules.length === 0 ? (
            <Typography variant="body1" color="text.secondary">
              No modules are available to you yet.
            </Typography>
          ) : (
            <Grid container spacing={2}>
              {modules.map(({ id, label, path, description, icon: Icon }) => (
                <Grid key={id} size={{ xs: 12, sm: 6, lg: 4 }}>
                  <Card>
                    <CardActionArea component={RouterLink} to={path}>
                      <CardContent>
                        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                          <Icon color="primary" fontSize="small" />
                          <Typography variant="subtitle1">{label}</Typography>
                        </Stack>
                        {description && (
                          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                            {description}
                          </Typography>
                        )}
                      </CardContent>
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
        </Grid>
      </Grid>
      <ConfirmDialog
        open={confirmOpen}
        title="Sign out other sessions?"
        message="Every other browser and device signed in to your account is signed out. This session stays signed in."
        confirmLabel="Sign out others"
        loading={signingOut}
        onConfirm={() => void signOutOthers()}
        onCancel={() => setConfirmOpen(false)}
      />
    </>
  );
}
