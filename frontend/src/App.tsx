import { useState } from 'react'

import { clearStoredSession, consumeSessionFromLocation, hasStoredSession } from './api'
import { AnalyticsView } from './components/AnalyticsView'
import { CreateUrlForm } from './components/CreateUrlForm'
import { LoginGate } from './components/LoginGate'
import { strings } from './strings'
import './App.css'

type Tab = 'create' | 'analytics'

/**
 * Two screens behind a tab switch, gated behind an explicit login.
 *
 * No router: with two views and no deep-linking requirement, a dependency would buy
 * nothing that local state does not already provide.
 */
export default function App() {
  // Consumed once, synchronously, before the first render decides whether to show the
  // login gate - otherwise a user landing back from Google would flash the gate before
  // the token from the URL fragment was picked up.
  const [signedIn, setSignedIn] = useState(() => {
    consumeSessionFromLocation()
    return hasStoredSession()
  })

  const [tab, setTab] = useState<Tab>('create')

  // Set when the user jumps from a freshly created link to its analytics, so the code
  // is filled in for them rather than needing to be copied across by hand.
  const [handedOverCode, setHandedOverCode] = useState<string | undefined>()

  // Analytics is mounted lazily but never unmounted afterwards. Rendering one screen
  // and discarding the other destroyed whatever the user had typed on the way out -
  // switching tabs mid-entry silently cleared the form. Keeping both mounted and
  // hiding the inactive one preserves in-progress input, which is the behaviour a tab
  // implies. Lazily on first visit so a user who never opens analytics does not pay
  // for its initial fetch.
  const [analyticsMounted, setAnalyticsMounted] = useState(false)

  function showAnalytics() {
    setAnalyticsMounted(true)
    setTab('analytics')
  }

  function showAnalyticsFor(shortCode: string) {
    setHandedOverCode(shortCode)
    showAnalytics()
  }

  function signOut() {
    clearStoredSession()
    setSignedIn(false)
    setTab('create')
    setAnalyticsMounted(false)
    setHandedOverCode(undefined)
  }

  if (!signedIn) {
    return (
      <main>
        <header>
          <h1>{strings.app.title}</h1>
          <p className="muted">{strings.app.tagline}</p>
        </header>
        <LoginGate onSignedIn={() => setSignedIn(true)} />
      </main>
    )
  }

  return (
    <main>
      <header>
        <h1>{strings.app.title}</h1>
        <p className="muted">{strings.app.tagline}</p>
        <button type="button" className="sign-out" onClick={signOut}>
          {strings.app.signOut}
        </button>
      </header>

      <nav className="tabs">
        <button
          type="button"
          className={tab === 'create' ? 'active' : ''}
          onClick={() => setTab('create')}
        >
          {strings.app.tabs.create}
        </button>
        <button
          type="button"
          className={tab === 'analytics' ? 'active' : ''}
          onClick={showAnalytics}
        >
          {strings.app.tabs.analytics}
        </button>
      </nav>

      <div hidden={tab !== 'create'}>
        <CreateUrlForm onViewAnalytics={showAnalyticsFor} />
      </div>

      {analyticsMounted && (
        <div hidden={tab !== 'analytics'}>
          {/* Remounts when the handed-over code changes, so the child picks up the new
              initial value instead of keeping the state it was first created with. */}
          <AnalyticsView key={handedOverCode} initialShortCode={handedOverCode} />
        </div>
      )}
    </main>
  )
}
