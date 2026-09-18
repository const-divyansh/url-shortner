/// <reference types="vite/client" />

/**
 * Types for the environment variables this app reads.
 *
 * Declaring them turns a typo in an env var name into a compile error rather than a
 * silent `undefined` that only surfaces as a broken request at runtime. Any new
 * `VITE_` variable must be added here and to `.env.example`.
 *
 * All values are optional: the app must run from a fresh clone with no `.env` file,
 * so `config.ts` supplies development fallbacks.
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_ANALYTICS_PAGE_SIZE?: string
  readonly VITE_COPY_FEEDBACK_MS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
