import { themeQuartz } from 'ag-grid-community';
import { surfaceColor } from './palette';
import { borderRadius, theme } from './theme';
import { baseFontSizePx } from './typography';

/**
 * AG Grid theme (Theming API) built from the MUI theme tokens, so font, colours, radius and
 * density stay in sync with the rest of the UI. Pass it to the grid via AppGrid only.
 */
export const agGridTheme = themeQuartz.withParams({
  fontFamily: theme.typography.fontFamily,
  fontSize: baseFontSizePx,
  headerFontSize: baseFontSizePx,
  headerFontWeight: 600,
  accentColor: theme.palette.primary.main,
  foregroundColor: theme.palette.text.primary,
  textColor: theme.palette.text.primary,
  backgroundColor: theme.palette.background.paper,
  borderColor: theme.palette.divider,
  headerBackgroundColor: surfaceColor,
  headerTextColor: theme.palette.text.secondary,
  rowHoverColor: theme.palette.action.hover,
  selectedRowBackgroundColor: theme.palette.action.selected,
  oddRowBackgroundColor: theme.palette.background.paper,
  borderRadius,
  wrapperBorderRadius: borderRadius,
  rowHeight: 40,
  headerHeight: 42,
  spacing: 6,
  browserColorScheme: 'light',
});
