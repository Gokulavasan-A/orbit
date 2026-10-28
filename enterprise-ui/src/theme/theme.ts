import { createTheme } from '@mui/material/styles';
import { palette, surfaceColor } from './palette';
import { typography } from './typography';

/** Corner radius in px shared by buttons, inputs, cards, dialogs and the grid. */
export const borderRadius = 8;

const softShadow = '0 1px 2px rgba(31, 41, 51, 0.06), 0 1px 3px rgba(31, 41, 51, 0.08)';
const raisedShadow = '0 4px 12px rgba(31, 41, 51, 0.10)';

export const theme = createTheme({
  palette,
  typography,
  shape: { borderRadius },
  spacing: 8,
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: '#FFFFFF' },
      },
    },
    MuiTypography: {
      defaultProps: {
        variantMapping: {
          pageTitle: 'h1',
          sectionTitle: 'h2',
          label: 'span',
          helperText: 'p',
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true, size: 'medium' },
      styleOverrides: {
        root: { textTransform: 'none', borderRadius, paddingInline: 16 },
      },
    },
    MuiIconButton: {
      defaultProps: { size: 'small' },
    },
    MuiTextField: {
      defaultProps: { variant: 'outlined', size: 'small', fullWidth: true },
    },
    MuiFormControl: {
      defaultProps: { size: 'small', fullWidth: true },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { borderRadius, backgroundColor: '#FFFFFF' },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: '#E3E8EE' },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { borderRadius, boxShadow: softShadow } },
    },
    MuiDialog: {
      styleOverrides: { paper: { borderRadius, boxShadow: raisedShadow } },
    },
    MuiMenu: {
      styleOverrides: { paper: { borderRadius, boxShadow: raisedShadow, border: '1px solid #E3E8EE' } },
    },
    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'inherit' },
      styleOverrides: { root: { borderBottom: '1px solid #E3E8EE', backgroundColor: '#FFFFFF' } },
    },
    MuiTable: {
      defaultProps: { size: 'small' },
    },
    MuiTableCell: {
      styleOverrides: { head: { fontWeight: 600, backgroundColor: surfaceColor } },
    },
    MuiChip: {
      defaultProps: { size: 'small' },
      styleOverrides: { root: { borderRadius: 6 } },
    },
    MuiTooltip: {
      defaultProps: { arrow: true },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius,
          '&.Mui-selected': { backgroundColor: 'rgba(59, 111, 182, 0.10)', color: '#2C5591' },
        },
      },
    },
    MuiAlert: {
      styleOverrides: { root: { borderRadius } },
    },
  },
});
