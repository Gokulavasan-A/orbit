import { createSlice, nanoid, type PayloadAction } from '@reduxjs/toolkit';

export type ToastSeverity = 'success' | 'info' | 'warning' | 'error';

export interface Toast {
  id: string;
  message: string;
  severity: ToastSeverity;
}

export interface UiState {
  /** Queue of toasts; AppToast shows the first one. */
  toasts: Toast[];
  /** Number of running non-background requests; > 0 shows the global progress bar. */
  pendingRequests: number;
}

const initialState: UiState = {
  toasts: [],
  pendingRequests: 0,
};

/** At most this many toasts are queued; older ones are dropped. */
const MAX_TOASTS = 5;

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    showToast: {
      reducer(state, action: PayloadAction<Toast>) {
        // Skip an identical toast that is already queued (e.g. several failed requests at once).
        if (state.toasts.some((t) => t.message === action.payload.message)) {
          return;
        }
        state.toasts.push(action.payload);
        if (state.toasts.length > MAX_TOASTS) {
          state.toasts.shift();
        }
      },
      prepare(message: string, severity: ToastSeverity = 'info') {
        return { payload: { id: nanoid(), message, severity } };
      },
    },
    dismissToast(state, action: PayloadAction<string>) {
      state.toasts = state.toasts.filter((t) => t.id !== action.payload);
    },
    requestStarted(state) {
      state.pendingRequests += 1;
    },
    requestFinished(state) {
      state.pendingRequests = Math.max(0, state.pendingRequests - 1);
    },
  },
  selectors: {
    selectToasts: (state) => state.toasts,
    selectIsGlobalLoading: (state) => state.pendingRequests > 0,
  },
});

export const { showToast, dismissToast, requestStarted, requestFinished } = uiSlice.actions;
export const { selectToasts, selectIsGlobalLoading } = uiSlice.selectors;
export default uiSlice.reducer;
export { uiSlice };
