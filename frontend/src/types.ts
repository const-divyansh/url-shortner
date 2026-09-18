/**
 * Shapes returned by the API.
 *
 * These mirror the backend DTOs (`CreateUrlResponse`, `AnalyticsResponse`,
 * `ErrorResponse`). They are a contract restated for the compiler, not a source of
 * truth - if the backend changes, these must change with it. Keeping them in one
 * file makes that a single, findable edit.
 *
 * Fields the backend may omit are optional here rather than nullable, because the
 * API omits null fields entirely (`@JsonInclude(NON_NULL)`) instead of sending nulls.
 */

/** Response to a successful `POST /api/urls`. */
export interface CreateUrlResponse {
  /** Full short URL, already assembled by the API - not just the code. */
  shortUrl: string
  shortCode: string
  targetUrl: string
  /** ISO-8601 instant. */
  createdAt: string
  /** ISO-8601 instant; absent when the link never expires. */
  expiresAt?: string
}

/** One recorded click. Carries no visitor identifier by design. */
export interface ClickEntry {
  occurredAt: string
  /** Absent when the visitor arrived directly. */
  referrer?: string
  userAgent?: string
}

/** Response to `GET /api/urls/{code}/analytics`. */
export interface AnalyticsResponse {
  shortCode: string
  targetUrl: string
  /** Total across all pages, not the length of `clicks`. */
  totalClicks: number
  /** Absent when the link has never been clicked. */
  lastClickAt?: string
  page: number
  pageSize: number
  clicks: ClickEntry[]
}

/** Response to creating or retrieving a browser-local session, guest or Google. */
export interface SessionResponse {
  token: string
  provider: string
}

/**
 * Who the API considers the caller to be.
 *
 * Fetched rather than remembered from whichever login button was pressed: a session
 * restored in a new tab, replaced by a different login, or cleared would leave that
 * local guess stale, and the UI would then offer actions the API will refuse.
 */
export interface SessionInfo {
  provider: string
  /**
   * True for an anonymous session. Sent by the API rather than derived from
   * `provider` here, so the frontend never re-implements a rule the API owns.
   */
  guest: boolean
}

/** One previously created link owned by the currently signed-in caller. */
export interface OwnedUrlSummary {
  shortCode: string
  shortUrl: string
  targetUrl: string
  createdAt: string
  expiresAt?: string
  totalClicks: number
  lastClickAt?: string
}

/** Response to `GET /api/shortcode/strategy`. */
export interface ShortCodeStrategy {
  /** Strategy currently used for new codes. */
  active: string
  /** Every strategy the server has registered. */
  available: string[]
}

/** Error body returned by the API for any failing request. */
export interface ErrorResponse {
  /** Stable machine-readable identifier, e.g. `alias.unavailable`. */
  code: string
  message: string
  timestamp: string
}
