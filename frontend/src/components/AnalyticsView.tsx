import { useCallback, useEffect, useRef, useState } from 'react'

import { ApiError, fetchAnalytics, fetchOwnedUrls, subscribeToClickEvents } from '../api'
import { ANALYTICS_PAGE_SIZE, COPY_FEEDBACK_MS } from '../config'
import { formatInstant, formatReferrer, formatUserAgent } from '../format'
import { strings } from '../strings'
import type { AnalyticsResponse, OwnedUrlSummary } from '../types'

interface Props {
  /** Code handed over from the create screen, if the user arrived that way. */
  initialShortCode?: string
}

/**
 * Screen two: look up a link's click statistics.
 *
 * Paging is server-side. Fetching everything and paging in the browser would move an
 * unbounded amount of data - a popular link has no upper bound on clicks - which is
 * also why the API caps page size.
 *
 * Fetches are triggered directly by the events that cause them rather than by an
 * effect watching state. Every load here follows a user action - submitting a code,
 * changing page, refreshing - so an effect would only add an indirection that reruns
 * on state changes it was never meant to react to. The one exception is the code
 * handed over from the create screen, which arrives as a prop.
 */
export function AnalyticsView({ initialShortCode }: Props) {
  const [codeInput, setCodeInput] = useState(initialShortCode ?? '')
  const [activeCode, setActiveCode] = useState(initialShortCode ?? '')
  const [page, setPage] = useState(0)

  const [data, setData] = useState<AnalyticsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [ownedLinks, setOwnedLinks] = useState<OwnedUrlSummary[]>([])
  const [ownedLinksLoading, setOwnedLinksLoading] = useState(true)
  const [ownedLinksError, setOwnedLinksError] = useState<string | null>(null)
  // Tracks which row most recently showed "Copied", if any - a single slot is enough
  // since only one click can be in flight at a time, and it doubles as the trigger
  // for the reveal-then-clear timer below.
  const [copiedCode, setCopiedCode] = useState<string | null>(null)

  // Clears the "Copied" confirmation after a moment, same contract as the copy
  // button on the create screen: without cleanup, unmounting mid-timeout would leave
  // a timer trying to set state on a gone component.
  useEffect(() => {
    if (!copiedCode) {
      return
    }
    const timer = setTimeout(() => setCopiedCode(null), COPY_FEEDBACK_MS)

    return () => clearTimeout(timer)
  }, [copiedCode])

  // Silent refresh: no spinner and no error surface. Its job is to keep the list in
  // step with the detail panel below it, which is exactly why it must not be able to
  // replace a good list with an error - the numbers the user came for are already on
  // screen.
  const refreshOwnedLinks = useCallback(async () => {
    try {
      setOwnedLinks(await fetchOwnedUrls())
    } catch {
      // Deliberately ignored - see above.
    }
  }, [])

  // Mirrors of state a stable callback needs to read without depending on it (which
  // would otherwise force the subscription effect below to tear down and reopen the
  // stream connection on every page/code change it was never meant to react to).
  const activeCodeRef = useRef(activeCode)
  const pageRef = useRef(page)
  useEffect(() => {
    activeCodeRef.current = activeCode
    pageRef.current = page
  }, [activeCode, page])

  // Same silent-refresh contract as refreshOwnedLinks above, for the detail panel:
  // a live click notification should update the numbers on screen without a spinner
  // or the possibility of replacing good data with an error.
  const refreshActiveDetail = useCallback(async () => {
    if (!activeCodeRef.current) {
      return
    }
    try {
      setData(await fetchAnalytics(activeCodeRef.current, pageRef.current, ANALYTICS_PAGE_SIZE))
    } catch {
      // Deliberately ignored - see above.
    }
  }, [])

  // Live updates: subscribed once for the lifetime of this screen (not per lookup),
  // so a click on any owned link refreshes both the list and whichever detail is
  // open, without the user ever pressing Refresh. Torn down on unmount, i.e. when
  // the user switches away from the Analytics tab, since App only mounts this
  // component while that tab is active.
  useEffect(() => {
    return subscribeToClickEvents(() => {
      void refreshOwnedLinks()
      void refreshActiveDetail()
    })
  }, [refreshOwnedLinks, refreshActiveDetail])

  async function handleCopyShortUrl(shortCode: string, shortUrl: string) {
    try {
      await navigator.clipboard.writeText(shortUrl)
      setCopiedCode(shortCode)
    } catch {
      // Same secure-context caveat as the create screen's copy button - reported
      // rather than silently doing nothing.
      setOwnedLinksError(strings.create.copyFailed)
    }
  }

  const load = useCallback(async (shortCode: string, pageToLoad: number) => {
    setActiveCode(shortCode)
    setPage(pageToLoad)
    setLoading(true)
    setError(null)

    try {
      setData(await fetchAnalytics(shortCode, pageToLoad, ANALYTICS_PAGE_SIZE))
      // Without this the list keeps whatever counts it happened to load at mount, so a
      // click recorded afterwards makes the same screen report two different totals for
      // one link.
      await refreshOwnedLinks()
    } catch (caught) {
      // Clearing the previous result matters: leaving stale numbers on screen beside
      // an error would misrepresent them as current.
      setData(null)
      setError(caught instanceof ApiError ? caught.message : strings.errors.unexpected)
    } finally {
      setLoading(false)
    }
  }, [refreshOwnedLinks])

  // The only load not caused by an event here: a code handed over from the create
  // screen arrives as a prop, so the first fetch has no user action to hang off.
  // The ref keeps it to once per mount - App remounts this component when the
  // handed-over code changes, so a later prop change cannot be missed.
  const autoLoaded = useRef(false)
  useEffect(() => {
    if (initialShortCode && !autoLoaded.current) {
      autoLoaded.current = true
      void load(initialShortCode, 0)
    }
  }, [initialShortCode, load])

  useEffect(() => {
    let cancelled = false

    void fetchOwnedUrls()
      .then((links) => {
        if (!cancelled) {
          setOwnedLinks(links)
        }
      })
      .catch((caught: unknown) => {
        if (cancelled) {
          return
        }
        setOwnedLinksError(
          caught instanceof ApiError
            ? caught.status === 0
              ? strings.errors.auth
              : caught.message
            : strings.errors.unexpected,
        )
      })
      .finally(() => {
        if (!cancelled) {
          setOwnedLinksLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const trimmed = codeInput.trim()
    if (!trimmed) {
      setError(strings.errors.requiredCode)
      return
    }
    // Always from page 0: keeping the current page across a lookup would land a new
    // code on page 5 of a history it may not have.
    void load(trimmed, 0)
  }

  // The API does not report a total page count, so "is there a next page" is inferred
  // from whether this page came back full. A final page that happens to be exactly
  // full offers a Next that lands on an empty page, which the table labels explicitly
  // rather than showing a blank area.
  const hasNextPage = data !== null && data.clicks.length === data.pageSize
  const hasPreviousPage = page > 0

  return (
    <section className="panel">
      <h2>{strings.analytics.heading}</h2>
      <p className="panel-lead">{strings.analytics.lead}</p>

      <section className="owned-links">
        <div className="owned-links-header">
          <h3>{strings.analytics.ownedLinksHeading}</h3>
          <p className="panel-lead owned-links-lead">
            {strings.analytics.ownedLinksLead}
          </p>
        </div>

        {ownedLinksError && <p className="error">{ownedLinksError}</p>}

        {ownedLinksLoading ? (
          <p className="empty-state">{strings.analytics.ownedLinksLoading}</p>
        ) : ownedLinks.length === 0 ? (
          <p className="empty-state">{strings.analytics.ownedLinksEmpty}</p>
        ) : (
          <div className="owned-link-list">
            {ownedLinks.map((link) => (
              <div
                key={link.shortCode}
                className={`owned-link-card${activeCode === link.shortCode ? ' is-active' : ''}`}
              >
                <button
                  type="button"
                  className="owned-link-select"
                  onClick={() => {
                    setCodeInput(link.shortCode)
                    void load(link.shortCode, 0)
                  }}
                >
                  <span className="owned-link-code">{link.shortCode}</span>
                  <span className="owned-link-target" title={link.targetUrl}>
                    {link.targetUrl}
                  </span>
                  <span className="owned-link-stats">
                    {/* Labelled rather than "0 · Never": two bare values separated by a
                        dot gave the reader nothing to tell a click count from a date. */}
                    <span className="owned-link-clicks">
                      {strings.analytics.clickCount(link.totalClicks)}
                    </span>
                    <span className="owned-link-last">
                      {link.lastClickAt
                        ? strings.analytics.lastClickAtLabel(formatInstant(link.lastClickAt))
                        : strings.analytics.neverClicked}
                    </span>
                  </span>
                </button>

                {/* Kept outside the selectable button above: a link/button nested
                    inside another interactive element is invalid HTML, and would make
                    "copy" also trigger "open analytics". */}
                <div className="owned-link-shorturl">
                  <a href={link.shortUrl} target="_blank" rel="noreferrer">
                    {link.shortUrl}
                  </a>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => handleCopyShortUrl(link.shortCode, link.shortUrl)}
                  >
                    {copiedCode === link.shortCode ? strings.create.copied : strings.create.copy}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <form onSubmit={handleSubmit} className="inline-form">
        <div className="field">
          <label htmlFor="shortCode">{strings.analytics.codeLabel}</label>
          <input
            id="shortCode"
            type="text"
            value={codeInput}
            placeholder={strings.analytics.codePlaceholder}
            onChange={(e) => setCodeInput(e.target.value)}
            autoComplete="off"
          />
        </div>
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? strings.analytics.submitting : strings.analytics.submit}
        </button>
      </form>

      {error && <p className="error" style={{ marginTop: '1.25rem' }}>{error}</p>}

      {data && (
        <>
          <div className="stats">
            <div className="stat">
              <span className="stat-label">{strings.analytics.totalClicks}</span>
              <span className="stat-value">{data.totalClicks}</span>
            </div>
            <div className="stat">
              <span className="stat-label">{strings.analytics.lastClick}</span>
              {/* Marked as text so a long timestamp does not stretch the card to a
                  different height than the numeric one beside it. */}
              <span className="stat-value is-text">
                {data.lastClickAt
                  ? formatInstant(data.lastClickAt)
                  : strings.analytics.never}
              </span>
            </div>
          </div>

          <dl className="meta">
            <dt>{strings.analytics.target}</dt>
            <dd>
              {/* Clamped to two lines and linked. Unclamped, a long target - an OAuth
                  callback carrying a token, say - pushes the click history off screen
                  and buries the data this page exists to show. */}
              <a
                className="target-url"
                href={data.targetUrl}
                target="_blank"
                rel="noreferrer"
                title={data.targetUrl}
              >
                {data.targetUrl}
              </a>
            </dd>
          </dl>

          <h3>{strings.analytics.historyHeading}</h3>

          {data.clicks.length === 0 ? (
            <p className="empty-state">
              {data.totalClicks === 0
                ? strings.analytics.noClicks
                : strings.analytics.noClicksOnPage}
            </p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>{strings.analytics.columnWhen}</th>
                  <th>{strings.analytics.columnReferrer}</th>
                  <th>{strings.analytics.columnUserAgent}</th>
                </tr>
              </thead>
              <tbody>
                {data.clicks.map((click, index) => (
                  // Index participates in the key because click entries carry no
                  // identifier, and the list is replaced wholesale per page rather
                  // than reordered.
                  <tr key={`${click.occurredAt}-${index}`}>
                    <td>{formatInstant(click.occurredAt)}</td>
                    <td title={click.referrer}>{formatReferrer(click.referrer)}</td>
                    <td title={click.userAgent}>{formatUserAgent(click.userAgent)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <div className="actions">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void load(activeCode, page - 1)}
              disabled={!hasPreviousPage || loading}
            >
              {strings.analytics.previous}
            </button>
            <span className="page-position">{strings.analytics.pagePosition(page)}</span>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void load(activeCode, page + 1)}
              disabled={!hasNextPage || loading}
            >
              {strings.analytics.next}
            </button>
            <span className="actions-spacer" />
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void load(activeCode, page)}
              disabled={loading}
            >
              {strings.analytics.refresh}
            </button>
          </div>
        </>
      )}
    </section>
  )
}
