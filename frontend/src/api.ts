import { Client as StompClient } from '@stomp/stompjs'

import { API_BASE_URL, CLICK_STREAM_RECONNECT_MS, SESSION_STORAGE_KEY, WS_BASE_URL } from './config'
import { strings } from './strings'
import type {
  AnalyticsResponse,
  CreateUrlResponse,
  ErrorResponse,
  OwnedUrlSummary,
  SessionResponse,
  ShortCodeStrategy,
} from './types'

/**
 * The only module that knows how to reach the API.
 *
 * Components call these functions; they never see a URL, an HTTP method, or a status
 * code. Endpoint changes stay local to this file rather than leaking into UI code.
 */

/**
 * Endpoint paths, relative to {@link API_BASE_URL}.
 *
 * Functions rather than concatenation at the call site, so each path shape is defined
 * once. The short code is URL-encoded because it comes from user input.
 */
const paths = {
  continueAsGuest: () => '/api/auth/guest',
  googleLoginStart: () => '/api/auth/google/start',
  createUrl: () => '/api/urls',
  ownedUrls: () => '/api/urls',
  strategy: () => '/api/shortcode/strategy',
  analytics: (shortCode: string) =>
    `/api/urls/${encodeURIComponent(shortCode)}/analytics`,
}

/** Path of the WebSocket endpoint, relative to {@link WS_BASE_URL}. */
const CLICK_EVENTS_WS_PATH = '/ws'

/** Destination the backend sends a caller's own click notifications to (per
 * {@code ClickBroadcaster}/{@code WebSocketConfig} on the backend) - fixed, because
 * Spring's STOMP user-destination routing already scopes it to the authenticated
 * caller's own connection, so the frontend never needs to know its own owner id. */
const CLICK_EVENTS_DESTINATION = '/user/queue/clicks'

/**
 * An error the API reported, as opposed to a transport failure.
 *
 * Carries the API's stable `code` so callers can branch without matching message
 * text, and the API's own message, which is what the user sees. The frontend
 * deliberately does not restate validation rules - the API owns them, so its message
 * is authoritative and stays correct when those rules change.
 */
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

/**
 * Performs a request and normalises both failure modes into an {@link ApiError}.
 *
 * Distinguishing them matters: a transport failure means the request never arrived,
 * so the user should check the backend rather than their input, while an API error
 * carries an explanation worth showing verbatim.
 */
type AuthMode = 'none' | 'required'

function readStoredSessionToken(): string | null {
  return window.localStorage.getItem(SESSION_STORAGE_KEY)
}

function storeSessionToken(token: string): void {
  window.localStorage.setItem(SESSION_STORAGE_KEY, token)
}

/**
 * Forgets the stored session so the app falls back to the login gate.
 *
 * Needed because the token is only half of the state: the server holds the matching
 * record, and it can disappear independently - a wiped database, a revoked session.
 * The browser cannot detect that in advance, so a 401 is the signal to sign in again.
 */
export function clearStoredSession(): void {
  window.localStorage.removeItem(SESSION_STORAGE_KEY)
}

/**
 * Whether this browser currently holds a session token.
 *
 * Login is now an explicit user action (guest or Google), never silently
 * auto-provisioned, so the app must check this before rendering protected screens
 * and show the login gate instead when it is false.
 */
export function hasStoredSession(): boolean {
  return readStoredSessionToken() !== null
}

/**
 * Explicit "continue as guest" login: mints a brand-new anonymous session.
 */
export async function continueAsGuest(): Promise<void> {
  const created = await request<SessionResponse>(
    paths.continueAsGuest(),
    { method: 'POST' },
    'none',
  )
  storeSessionToken(created.token)
}

/**
 * Sends the browser to Google's consent screen via a full-page navigation.
 *
 * This is not an XHR/fetch call - the backend redirects on to Google, then Google
 * redirects back to the backend's callback, which finally redirects here with the
 * session token in the URL fragment. See {@link consumeSessionFromLocation}.
 */
export function startGoogleLogin(): void {
  window.location.assign(`${API_BASE_URL}${paths.googleLoginStart()}`)
}

/**
 * Picks up a session token left in the URL fragment by the Google login callback,
 * stores it, and strips it from the visible URL so it is never left sitting in
 * browser history or shared if the page is bookmarked.
 *
 * A fragment (`#session=...`), not a query parameter, is what the backend uses
 * precisely so this value is never sent to any server, including this one.
 */
export function consumeSessionFromLocation(): void {
  const hash = window.location.hash
  const match = /^#session=(.+)$/.exec(hash)

  if (!match) {
    return
  }

  storeSessionToken(decodeURIComponent(match[1]))
  window.history.replaceState(null, '', window.location.pathname + window.location.search)
}

async function buildHeaders(
  headers: HeadersInit | undefined,
  authMode: AuthMode,
): Promise<HeadersInit | undefined> {
  const merged = new Headers(headers)

  if (authMode === 'required') {
    const token = readStoredSessionToken()
    if (token === null) {
      // Fails fast, client-side: without an explicit login there is no token to send,
      // and the caller (the auth gate) should be showing the login screen already.
      throw new ApiError('auth.required', strings.errors.auth, 401)
    }
    merged.set('Authorization', `Bearer ${token}`)
  }

  return Array.from(merged.keys()).length === 0 ? undefined : merged
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  authMode: AuthMode = 'none',
): Promise<T> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: await buildHeaders(options.headers, authMode),
    })
  } catch {
    // fetch rejects only on transport problems - unreachable host, DNS failure, or a
    // cross-origin request the browser refused. An HTTP error status resolves normally.
    throw new ApiError('network', strings.errors.network, 0)
  }

  // A failing response may carry no JSON body at all - a proxy error page, for
  // example - so parsing is attempted separately from deciding success.
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (!response.ok) {
    const error = body as ErrorResponse | null

    // The stored session no longer matches any server record - the database was
    // reset, or the session was revoked. Clearing it here (rather than silently
    // re-minting a new one, as the old API-key flow did) sends the user back to an
    // explicit login gate instead of a screen that looks logged in but is not.
    if (authMode === 'required' && response.status === 401 && error?.code === 'auth.invalid') {
      clearStoredSession()
    }

    throw new ApiError(
      error?.code ?? 'unexpected',
      error?.message ?? strings.errors.unexpected,
      response.status,
    )
  }

  return body as T
}

export interface CreateShortUrlInput {
  targetUrl: string
  customAlias?: string
  expiresAt?: string
}

/**
 * Creates a short URL.
 *
 * Optional fields are omitted rather than sent as empty strings. The API treats a
 * blank alias as absent, but sending `""` leans on that leniency instead of stating
 * the intent.
 */
export function createShortUrl(
  input: CreateShortUrlInput,
): Promise<CreateUrlResponse> {
  const payload: CreateShortUrlInput = { targetUrl: input.targetUrl }

  if (input.customAlias) {
    payload.customAlias = input.customAlias
  }
  if (input.expiresAt) {
    payload.expiresAt = input.expiresAt
  }

  return request<CreateUrlResponse>(paths.createUrl(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }, 'required')
}

/**
 * Fetches one page of analytics for a short code.
 */
export function fetchAnalytics(
  shortCode: string,
  page: number,
  size: number,
): Promise<AnalyticsResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })

  return request<AnalyticsResponse>(`${paths.analytics(shortCode)}?${query}`, {}, 'required')
}

/**
 * Lists the links owned by the current browser session.
 */
export function fetchOwnedUrls(): Promise<OwnedUrlSummary[]> {
  return request<OwnedUrlSummary[]>(paths.ownedUrls(), {}, 'required')
}

/**
 * Subscribes to a live stream of click notifications for the caller's own links.
 *
 * Built on STOMP over a native `WebSocket` (not `EventSource`, and not a bare
 * `WebSocket` either): a bare `WebSocket` has no message framing of its own, so
 * "subscribe to just my clicks" would have to be invented here; STOMP already defines
 * that as a destination subscription, and the backend broker already scopes
 * `/user/queue/clicks` to the authenticated caller's own connection.
 *
 * The session token cannot travel as an `Authorization` header - like `EventSource`,
 * the browser's native `WebSocket` API has no way to set custom headers on the
 * handshake - so it travels as a `?token=` query parameter on the handshake URL
 * instead, read by the backend's `WebSocketAuthHandshakeInterceptor`.
 *
 * Calls `onClick` once per click notification (never with a payload - the count itself
 * always comes from a real `GET`, so there is exactly one source of truth for it).
 * Reconnects automatically after a drop (`reconnectDelay`); returns an unsubscribe
 * function that stops this permanently.
 *
 * The token is read once, when the caller subscribes, and reused for every automatic
 * reconnect afterwards - fine in practice, since a token change (sign-out/sign-in)
 * unmounts the screen that holds this subscription rather than swapping the token
 * underneath it.
 */
export function subscribeToClickEvents(onClick: () => void): () => void {
  const token = readStoredSessionToken()
  if (token === null) {
    // No session, nothing to subscribe to - the login gate is already showing.
    return () => {}
  }

  const client = new StompClient({
    brokerURL: `${WS_BASE_URL}${CLICK_EVENTS_WS_PATH}?token=${encodeURIComponent(token)}`,
    reconnectDelay: CLICK_STREAM_RECONNECT_MS,
    // The default logs every STOMP frame to the console; this channel is a background
    // implementation detail the user never needs to see logged.
    debug: () => {},
    onConnect: () => {
      client.subscribe(CLICK_EVENTS_DESTINATION, () => onClick())
    },
  })

  client.activate()

  return () => {
    void client.deactivate()
  }
}

/**
 * Reads the short-code strategy currently in force.
 */
export function fetchShortCodeStrategy(): Promise<ShortCodeStrategy> {
  return request<ShortCodeStrategy>(paths.strategy(), {}, 'required')
}

/**
 * Switches the active short-code strategy.
 *
 * Server-wide, not per-user: the change affects every link created afterwards, by
 * anyone. Existing codes keep resolving, because lookup does not depend on which
 * algorithm produced them.
 */
export function updateShortCodeStrategy(
  strategy: string,
): Promise<ShortCodeStrategy> {
  return request<ShortCodeStrategy>(
    paths.strategy(),
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ strategy }),
    },
    'required',
  )
}
