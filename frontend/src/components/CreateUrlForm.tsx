import { useEffect, useState } from 'react'

import { ApiError, createShortUrl } from '../api'
import { COPY_FEEDBACK_MS } from '../config'
import { formatInstant, localInputToIso } from '../format'
import { strings } from '../strings'
import type { CreateUrlResponse } from '../types'
import { StrategySelector } from './StrategySelector'

const DEFAULT_EXPIRY_OFFSET_HOURS = 24
const SECONDS_PER_MINUTE = 60
const MILLISECONDS_PER_SECOND = 1000

function toLocalDateTimeInputValue(date: Date): string {
  const timezoneOffsetMs =
    date.getTimezoneOffset() *
    SECONDS_PER_MINUTE *
    MILLISECONDS_PER_SECOND

  return new Date(date.getTime() - timezoneOffsetMs).toISOString().slice(0, 16)
}

function buildDefaultExpiryLocal(): string {
  const defaultExpiry = new Date()

  defaultExpiry.setSeconds(0, 0)
  defaultExpiry.setHours(defaultExpiry.getHours() + DEFAULT_EXPIRY_OFFSET_HOURS)

  return toLocalDateTimeInputValue(defaultExpiry)
}

interface Props {
  /** Lets the result panel hand the new code straight to the analytics screen. */
  onViewAnalytics: (shortCode: string) => void
}

/**
 * Screen one: create a short link.
 *
 * Deliberately does not validate the URL itself. The API owns those rules - scheme
 * allow-list, SSRF host checks, alias charset, reserved aliases - and duplicating any
 * of them here would produce a second, drifting copy that contradicts the server the
 * moment the server changes. The only client-side check is for an empty field, which
 * avoids a round trip that could not possibly succeed.
 */
export function CreateUrlForm({ onViewAnalytics }: Props) {
  const [targetUrl, setTargetUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [expiresAtLocal, setExpiresAtLocal] = useState('')

  // Expiry is opt-in. An always-visible date input renders the browser's native
  // "dd/mm/yyyy, --:-- --" placeholder, which reads as a broken field rather than an
  // empty optional one - so the input only appears once the user asks for it.
  const [wantsExpiry, setWantsExpiry] = useState(false)

  const [result, setResult] = useState<CreateUrlResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [copied, setCopied] = useState(false)

  // Clears the "Copied" confirmation after a moment. The cleanup matters: without it,
  // unmounting mid-timeout would leave a timer trying to set state on a gone component.
  useEffect(() => {
    if (!copied) {
      return
    }
    const timer = setTimeout(() => setCopied(false), COPY_FEEDBACK_MS)

    return () => clearTimeout(timer)
  }, [copied])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const trimmedUrl = targetUrl.trim()
    if (!trimmedUrl) {
      setError(strings.errors.requiredUrl)
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const created = await createShortUrl({
        targetUrl: trimmedUrl,
        customAlias: customAlias.trim() || undefined,
        // Converted to UTC here: the input yields wall-clock text with no zone, which
        // the API would otherwise read as UTC and shift by the user's offset.
        expiresAt: localInputToIso(expiresAtLocal),
      })
      setResult(created)
    } catch (caught) {
      // The API's message is shown as-is - it already explains precisely which rule
      // was broken, and it stays correct when those rules change.
      setError(
        caught instanceof ApiError ? caught.message : strings.errors.unexpected,
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCopy(value: string) {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
    } catch {
      // The Clipboard API requires a secure context, so this fails over plain HTTP on
      // anything but localhost. Reported rather than silently doing nothing.
      setError(strings.create.copyFailed)
    }
  }

  function reset() {
    setResult(null)
    setError(null)
    setTargetUrl('')
    setCustomAlias('')
    setExpiresAtLocal('')
    setWantsExpiry(false)
  }

  if (result) {
    return (
      <section className="panel">
        <h2>{strings.create.resultHeading}</h2>
        <p className="panel-lead">{strings.create.resultLead}</p>

        <div className="result-url">
          <a href={result.shortUrl} target="_blank" rel="noreferrer">
            {result.shortUrl}
          </a>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => handleCopy(result.shortUrl)}
          >
            {copied ? strings.create.copied : strings.create.copy}
          </button>
        </div>

        <dl className="meta">
          <dt>{strings.analytics.target}</dt>
          <dd>
            {/* Clamped and linked rather than printed raw: a target can be thousands
                of characters, which would otherwise dominate the panel. */}
            <a
              className="target-url"
              href={result.targetUrl}
              target="_blank"
              rel="noreferrer"
              title={result.targetUrl}
            >
              {result.targetUrl}
            </a>
          </dd>
        </dl>

        <div className="timestamps">
          <span>{strings.create.createdAt(formatInstant(result.createdAt))}</span>
          <span>
            {result.expiresAt
              ? strings.create.expiresAt(formatInstant(result.expiresAt))
              : strings.create.neverExpires}
          </span>
        </div>

        {error && <p className="error">{error}</p>}

        <div className="actions">
          <button type="button" className="btn-secondary" onClick={reset}>
            {strings.create.another}
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={() => onViewAnalytics(result.shortCode)}
          >
            {strings.create.viewAnalytics}
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="panel">
      <h2>{strings.create.heading}</h2>
      <p className="panel-lead">{strings.create.lead}</p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="targetUrl">{strings.create.targetUrlLabel}</label>
          <input
            id="targetUrl"
            type="text"
            value={targetUrl}
            placeholder={strings.create.targetUrlPlaceholder}
            onChange={(e) => setTargetUrl(e.target.value)}
            // Deliberately type="text", not type="url": the browser's own validation
            // would reject values before the API sees them, so the user would get a
            // generic bubble instead of the API's specific explanation.
            autoComplete="off"
          />
        </div>

        <div className="field">
          <label htmlFor="customAlias">
            {strings.create.aliasLabel}{' '}
            <span className="label-optional">({strings.create.optional})</span>
          </label>
          <input
            id="customAlias"
            type="text"
            value={customAlias}
            placeholder={strings.create.aliasPlaceholder}
            onChange={(e) => setCustomAlias(e.target.value)}
            autoComplete="off"
          />
        </div>

        <div className="field">
          <label className="checkbox">
            <input
              type="checkbox"
              checked={wantsExpiry}
              onChange={(e) => {
                setWantsExpiry(e.target.checked)
                if (e.target.checked && !expiresAtLocal) {
                  // Pre-filling avoids the browser's empty native placeholder, which
                  // reads like a broken control rather than an optional date.
                  setExpiresAtLocal(buildDefaultExpiryLocal())
                }
                // Clearing on un-tick matters: a value left behind would still be
                // sent, silently expiring a link the user decided should not expire.
                if (!e.target.checked) {
                  setExpiresAtLocal('')
                }
              }}
            />
            <span>{strings.create.expiresToggle}</span>
          </label>

          {wantsExpiry && (
            <div className="field-nested">
              <label htmlFor="expiresAt">{strings.create.expiresLabel}</label>
              <input
                id="expiresAt"
                type="datetime-local"
                value={expiresAtLocal}
                onChange={(e) => setExpiresAtLocal(e.target.value)}
              />
              <p className="hint">{strings.create.expiresHint}</p>
            </div>
          )}
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? strings.create.submitting : strings.create.submit}
        </button>
      </form>

      <StrategySelector />
    </section>
  )
}
