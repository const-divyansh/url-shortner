import { useState } from 'react'

import { ApiError, continueAsGuest, startGoogleLogin } from '../api'
import { strings } from '../strings'

interface Props {
  /** Called once a session has been established, so the parent can show the app. */
  onSignedIn: () => void
}

/**
 * The explicit login gate shown before any protected screen.
 *
 * Replaces the previous behaviour of silently minting an API key on first load: a
 * user should always know they made a choice to continue as a guest, and Google is
 * offered as the alternative that lets them reach their links from another device.
 */
export function LoginGate({ onSignedIn }: Props) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleGuest() {
    setSubmitting(true)
    setError(null)

    try {
      await continueAsGuest()
      onSignedIn()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.errors.unexpected)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-gate">
      <h2>{strings.auth.heading}</h2>
      <p className="muted">{strings.auth.lead}</p>

      <div className="login-gate-options">
        <button type="button" className="btn-primary" onClick={startGoogleLogin} disabled={submitting}>
          {strings.auth.googleButton}
        </button>
        <p className="login-gate-hint">{strings.auth.googleHint}</p>

        <div className="login-gate-divider">{strings.auth.or}</div>

        <button type="button" className="btn-secondary" onClick={handleGuest} disabled={submitting}>
          {submitting ? strings.auth.guestSubmitting : strings.auth.guestButton}
        </button>
        <p className="login-gate-hint">{strings.auth.guestHint}</p>
      </div>

      {error && <p className="error">{error}</p>}
    </div>
  )
}
