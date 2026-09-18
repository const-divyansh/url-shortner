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
 *
 * Raw user-agent strings are ~120 characters of vendor boilerplate, so a table of
 * them is unreadable and every row looks identical. This reduces one to the only two
 * facts a reader is actually scanning for - which browser, which platform.
 *
 * The full string is still handed to the table as the cell's `title`, so nothing is
 * lost; this is presentation only, and the stored value is untouched.
 */
export function formatUserAgent(userAgent: string | undefined): string {
  if (!userAgent) {
    return strings.analytics.unknownAgent
  }

  const browser = detectBrowser(userAgent)
  const os = detectOs(userAgent)

  if (browser && os) {
    return strings.analytics.agentLabel(browser, os)
  }

  // One half is better than nothing, but a string we could not read at all - a bot, a
  // crawler, a scripted client - is returned verbatim rather than labelled with a
  // guess. A wrong label is worse than a long one: it reads as fact.
  return browser ?? os ?? userAgent
}

/**
 * Browser detection patterns, **most specific first** - the order is the logic.
 *
 * Vendors deliberately impersonate each other for compatibility: Edge's user agent
 * contains `Chrome`, Chrome's contains `Safari`, and Opera's contains both. Matching
 * in declaration order is what keeps Edge from being reported as Chrome and Chrome
 * from being reported as Safari, so entries must never be reordered casually.
 *
 * Safari is matched on `Version/` rather than `Safari/`, because the `Safari/537.36`
 * token is a frozen WebKit build number that every WebKit-derived browser carries and
 * that no longer tracks any real Safari release.
 *
 * Kept here rather than in `strings.ts` even though the names are user-visible: these
 * are product names that never translate, and each is meaningless apart from the
 * pattern that detects it. Splitting the pair across two files would invite drift.
 */
const BROWSER_PATTERNS: ReadonlyArray<readonly [RegExp, string]> = [
  [/\bEdg(?:e|A|iOS)?\/(\d+)/, 'Edge'],
  [/\bOPR\/(\d+)/, 'Opera'],
  [/\bSamsungBrowser\/(\d+)/, 'Samsung Internet'],
  [/\b(?:Firefox|FxiOS)\/(\d+)/, 'Firefox'],
  [/\b(?:Chrome|CriOS)\/(\d+)/, 'Chrome'],
  [/\bVersion\/(\d+).*\bSafari\//, 'Safari'],
]

/**
 * Platform patterns, **most specific first** for the same reason as above: an iPad
 * reports `Mac OS X`, and Android reports `Linux`, so the broader match has to come
 * second or it swallows the narrower one.
 */
const OS_PATTERNS: ReadonlyArray<readonly [RegExp, string]> = [
  [/\b(?:iPhone|iPad|iPod)\b/, 'iOS'],
  [/\bAndroid\b/, 'Android'],
  [/\b(?:Mac OS X|Macintosh)\b/, 'macOS'],
  [/\bWindows NT\b/, 'Windows'],
  [/\b(?:Linux|X11)\b/, 'Linux'],
]

/** Returns e.g. `Chrome 153`, or null when no known browser is recognised. */
function detectBrowser(userAgent: string): string | null {
  for (const [pattern, name] of BROWSER_PATTERNS) {
    const match = pattern.exec(userAgent)
    if (match) {
      // Major version only. The full quad ("153.0.0.0") is noise in a table cell, and
      // the trailing zeros are placeholders Chrome stopped populating years ago.
      return `${name} ${match[1]}`
    }
  }

  return null
}

/** Returns e.g. `macOS`, or null when no known platform is recognised. */
function detectOs(userAgent: string): string | null {
  for (const [pattern, name] of OS_PATTERNS) {
    if (pattern.test(userAgent)) {
      return name
    }
  }

  return null
}
