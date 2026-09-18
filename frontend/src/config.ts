/**
 * Application configuration.
 *
 * Every externally-controlled value enters the app here and nowhere else, so
 * deploying to a different environment means changing env vars rather than code.
 *
 * Vite only exposes variables prefixed `VITE_` to the browser bundle. That is a
 * safety feature, not an inconvenience: everything here ships to the client and is
 * readable by anyone, so no secret may ever be placed in this file or in `.env`.
 */

/**
 * Reads a numeric env var, falling back when it is absent or not a number.
 *
 * `Number('')` is `0` and `Number('abc')` is `NaN`, either of which would propagate
 * into the UI as a broken page size or a timer that never fires. Validating here
 * keeps one bad env value from becoming a confusing runtime bug.
 */
function numberFromEnv(raw: string | undefined, fallback: number): number {
  const parsed = Number(raw)

  return raw !== undefined && Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

/**
 * Base URL of the API.
 *
 * The fallback is the local backend default so a fresh clone runs without setup. It
 * is a development convenience only - any real deployment must set
 * `VITE_API_BASE_URL`, since the API almost certainly is not on localhost.
 */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * WebSocket origin for the live click-notification channel, derived from
 * {@link API_BASE_URL} rather than a separate env var: it is always the same host,
 * just reached over `ws(s)://` instead of `http(s)://`, so a second variable would be
 * one more place a deployment could set inconsistently.
 */
export const WS_BASE_URL: string = API_BASE_URL.replace(/^http/, 'ws')

/**
 * Click-history page size for the analytics screen.
 *
 * The API caps this server-side; requesting more simply returns the cap.
 */
export const ANALYTICS_PAGE_SIZE: number = numberFromEnv(
  import.meta.env.VITE_ANALYTICS_PAGE_SIZE,
  20,
)

/**
 * How long the "copied" confirmation stays visible, in milliseconds.
 */
export const COPY_FEEDBACK_MS: number = numberFromEnv(
  import.meta.env.VITE_COPY_FEEDBACK_MS,
  2000,
)

/**
 * Local-storage key holding the browser's session token, however it was obtained
 * (guest or Google login).
 *
 * Stored centrally so a future login mode can reuse it without hunting for string
 * literals across components and API helpers.
 */
export const SESSION_STORAGE_KEY = 'url-shortener.session-token'

/**
 * How long to wait before reopening the live click-events WebSocket after it drops
 * (network blip, server restart, an intermediary closing an idle connection).
 *
 * Fixed rather than exponential backoff: this is a single low-cost reconnect to one
 * endpoint the user is actively looking at, not a fan-out of retries against a
 * struggling server, so there is no thundering-herd risk to back off from.
 */
export const CLICK_STREAM_RECONNECT_MS: number = numberFromEnv(
  import.meta.env.VITE_CLICK_STREAM_RECONNECT_MS,
  3000,
)

