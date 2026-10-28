import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Toolbar from '@mui/material/Toolbar';
import { useState } from 'react';
import { Outlet } from 'react-router';
import { SideNav } from './components/SideNav';
import { TopBar } from './components/TopBar';

const NAV_WIDTH = 232;

/** Signed-in layout: top bar, left navigation (permanent on md+, a drawer below) and content. */
export function MainLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <TopBar onToggleNav={() => setMobileNavOpen((open) => !open)} />

      <Drawer
        variant="temporary"
        open={mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
        sx={{ display: { xs: 'block', md: 'none' }, '& .MuiDrawer-paper': { width: NAV_WIDTH } }}
      >
        <Toolbar variant="dense" sx={{ minHeight: 56 }} />
        <SideNav onNavigate={() => setMobileNavOpen(false)} />
      </Drawer>
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          width: NAV_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: NAV_WIDTH, boxSizing: 'border-box', borderColor: 'divider' },
        }}
      >
        <Toolbar variant="dense" sx={{ minHeight: 56 }} />
        <SideNav />
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, sm: 3 } }}>
        <Toolbar variant="dense" sx={{ minHeight: 56 }} />
        <Outlet />
      </Box>
    </Box>
  );
}
