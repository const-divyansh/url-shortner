import { defineConfig, devices } from '@playwright/test'

const FRONTEND_BASE_URL =
  process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['list']],
  use: {
    baseURL: FRONTEND_BASE_URL,
    trace: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host localhost --port 5173',
    url: FRONTEND_BASE_URL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
