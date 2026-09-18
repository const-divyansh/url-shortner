import { strings } from './strings'

/**
 * Display formatting.
 *
 * Kept out of components so JSX stays declarative, and so date and URL handling is
 * defined once rather than being re-derived slightly differently per screen.
 */

/**
 * Formats an ISO-8601 instant for display in the viewer's own locale and timezone.
 *
 * No locale is passed to `toLocaleString`, so the browser's setting is used. The API
 * returns UTC instants; showing raw UTC would be accurate but consistently confusing
 * to anyone not in that zone.
 */
export function formatInstant(iso: string | undefined): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)

  // An unparseable value yields an Invalid Date, whose toLocaleString is the literal
  // text "Invalid Date". Returning the raw value is more useful for diagnosis.
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

/**
 * Converts a local `datetime-local` input value into the UTC instant the API expects.
 *
 * `datetime-local` yields wall-clock text with no timezone ("2026-10-01T09:00"), so
 * sending it unchanged would be interpreted as UTC and silently shift the expiry by
 * the user's offset. Constructing a Date applies the local zone, and toISOString then
 * converts to UTC.
 */
export function localInputToIso(value: string): string | undefined {
  if (!value) {
    return undefined
  }
  const date = new Date(value)

  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

/**
 * Renders a referrer for the history table, naming the absent case explicitly.
 *
 * An empty cell would be ambiguous - it could mean "no referrer" or "we failed to
 * record one". "Direct" says which.
 *
 * Overflow is left to CSS (`text-overflow: ellipsis`) rather than cut to a fixed
 * character count here: the table is responsive, so the number of characters that
 * actually fit depends on the viewport. Truncating in both places would also produce
 * a doubled ellipsis.
 */
export function formatReferrer(referrer: string | undefined): string {
  return referrer ?? strings.analytics.directVisit
}

/**
 * Renders a user agent for the history table.
 */
export function formatUserAgent(userAgent: string | undefined): string {
  return userAgent ?? strings.analytics.unknownAgent
}
