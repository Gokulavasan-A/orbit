import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // Same origin as VITE_API_BASE_URL below, so jsdom does not send CORS preflights that MSW
      // would have to answer. Real CORS is verified against the running API, not here.
      environmentOptions: { jsdom: { url: 'http://api.test/' } },
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
      restoreMocks: true,
      env: {
        VITE_API_BASE_URL: 'http://api.test',
        VITE_APP_NAME: 'Enterprise Base',
        VITE_SESSION_IDLE_TIMEOUT_MINUTES: '30',
        VITE_SESSION_IDLE_WARNING_SECONDS: '120',
      },
    },
  }),
);
