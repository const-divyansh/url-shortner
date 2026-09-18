import { useEffect, useState } from 'react'

import { ApiError, fetchShortCodeStrategy, updateShortCodeStrategy } from '../api'
import { strings } from '../strings'
import type { ShortCodeStrategy } from '../types'

/**
 * Shows which algorithm is generating short codes, and allows switching it.
 *
 * The API owns the list of strategies: this renders whatever the server reports rather
 * than hardcoding names, so a strategy added server-side appears here without a
 * frontend change. Unknown names fall back to their raw value instead of rendering
 * blank.
 *
 * The control is deliberately explicit that this is server-wide state. It is not a
 * per-user preference - switching changes generation for every subsequent link created
 * by anyone, which a plain dropdown would otherwise imply is personal.
 */
export function StrategySelector() {
  const [strategy, setStrategy] = useState<ShortCodeStrategy | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState(false)

  useEffect(() => {
    let cancelled = false

    void fetchShortCodeStrategy()
      .then((loaded) => {
        if (!cancelled) {
          setStrategy(loaded)
        }
      })
      .catch((caught: unknown) => {
        if (!cancelled) {
          setError(
            caught instanceof ApiError ? caught.message : strings.errors.unexpected,
          )
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  async function handleChange(next: string) {
    setUpdating(true)
    setError(null)

    try {
      setStrategy(await updateShortCodeStrategy(next))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : strings.errors.unexpected)
    } finally {
      setUpdating(false)
    }
  }

  function label(name: string): string {
    return strings.strategy.names[name] ?? name
  }

  return (
    <div className="strategy">
      <div className="strategy-row">
        <label htmlFor="strategy">{strings.strategy.label}</label>
        <select
          id="strategy"
          value={strategy?.active ?? ''}
          disabled={strategy === null || updating}
          onChange={(e) => void handleChange(e.target.value)}
        >
          {strategy === null ? (
            <option value="">{strings.strategy.loading}</option>
          ) : (
            strategy.available.map((name) => (
              <option key={name} value={name}>
                {label(name)}
              </option>
            ))
          )}
        </select>
      </div>

      {strategy && (
        <p className="hint">
          {updating
            ? strings.strategy.updating
            : (strings.strategy.descriptions[strategy.active] ?? '')}
        </p>
      )}

      <p className="hint strategy-scope">{strings.strategy.scopeWarning}</p>

      {error && <p className="error">{error}</p>}
    </div>
  )
}
