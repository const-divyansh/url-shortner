/**
 * User-facing text.
 *
 * Centralised rather than inlined in components so wording changes in one place, and
 * so introducing an i18n library later is a swap of this module rather than a rewrite
 * of every component. ADR-006 deliberately drops the library as disproportionate for
 * two screens, but keeps the indirection it would have needed.
 *
 * Values that vary are functions rather than fragments assembled at the call site, so
 * sentence structure stays here where it can be changed as a whole.
 */
export const strings = {
  app: {
    title: 'URL Shortener',
    tagline: 'Shorten a link, then track how it performs.',
    tabs: {
      create: 'Shorten',
      analytics: 'Analytics',
    },
    signOut: 'Sign out',
  },

  auth: {
    heading: 'Sign in to continue',
    lead: 'Sign in to create short links and see their analytics.',
    guestButton: 'Continue as guest',
    guestHint: 'No account needed. Links stay tied to this browser only.',
    googleButton: 'Sign in with Google',
    googleHint: 'Recommended if you want to reach your links from another device.',
    or: 'or',
    guestSubmitting: 'Setting up your guest session…',
  },

  create: {
    heading: 'Shorten a link',
    lead: 'Paste a long URL and get a short one back. Custom alias and expiry are optional.',
    resultLead: 'Your link is live. Share it anywhere.',
    targetUrlLabel: 'Long URL',
    targetUrlPlaceholder: 'https://example.com/a/very/long/path',
    aliasLabel: 'Custom alias',
    aliasPlaceholder: 'my-link',
    expiresLabel: 'Expires at',
    expiresToggle: 'Set an expiry date',
    expiresHint: 'The link stops working after this time.',
    optional: 'optional',
    submit: 'Shorten',
    submitting: 'Shortening…',
    resultHeading: 'Your short link',
    copy: 'Copy',
    copied: 'Copied',
    copyFailed: 'Copy failed',
    createdAt: (when: string) => `Created ${when}`,
    expiresAt: (when: string) => `Expires ${when}`,
    neverExpires: 'Never expires',
    another: 'Shorten another',
    viewAnalytics: 'View analytics',
  },

  strategy: {
    label: 'Code generation',
    loading: 'Checking…',
    /**
     * Friendly names for the strategies the API ships (ADR-007). Falls back to the
     * raw server name, so a strategy added later still renders rather than showing
     * an empty control.
     */
    names: {
      'base62-random': 'Random (Base62)',
      'hash-url': 'Hash of URL',
    } as Record<string, string>,
    descriptions: {
      'base62-random': 'Unpredictable codes. The same link shortened twice gets two different codes.',
      'hash-url': 'Codes derived from the URL itself, so they are repeatable but guessable.',
    } as Record<string, string>,
    /**
     * Stated plainly because this is server-wide state, not a personal preference -
     * a user who changes it for everyone.
     */
    scopeWarning: 'Applies to every new link, for everyone. Existing links keep working.',
    updating: 'Switching…',
  },

  analytics: {
    heading: 'Link analytics',
    lead: 'Enter a short code to see how its link is performing.',
    ownedLinksHeading: 'Your links',
    ownedLinksLead: 'Reopen analytics for links created from this browser.',
    ownedLinksLoading: 'Loading your links…',
    ownedLinksEmpty: 'No links have been created from this browser yet.',
    codeLabel: 'Short code',
    codePlaceholder: 'abc1234',
    submit: 'Look up',
    submitting: 'Looking up…',
    totalClicks: 'Total clicks',
    lastClick: 'Last click',
    never: 'Never',
    target: 'Points to',
    /** Pluralised because "1 clicks" reads as a bug to the user. */
    clickCount: (total: number) => (total === 1 ? '1 click' : `${total} clicks`),
    lastClickAtLabel: (when: string) => `Last click ${when}`,
    neverClicked: 'No clicks yet',
    historyHeading: 'Recent clicks',
    noClicks: 'No clicks recorded yet.',
    noClicksOnPage: 'No clicks on this page.',
    columnWhen: 'When',
    columnReferrer: 'Referrer',
    columnUserAgent: 'User agent',
    directVisit: 'Direct',
    unknownAgent: 'Unknown',
    previous: 'Previous',
    next: 'Next',
    pagePosition: (page: number) => `Page ${page + 1}`,
    refresh: 'Refresh',
  },

  errors: {
    /**
     * Shown when the request never reached the API - wrong base URL, backend down, or
     * a cross-origin call the browser refused. Deliberately points at the API rather
     * than blaming the user's input.
     */
    network: 'Could not reach the API. Check that the backend is running.',
    /** Fallback when the API failed without a message we can show. */
    unexpected: 'Something went wrong. Please try again.',
    auth: 'Please sign in to continue.',
    requiredUrl: 'Enter a URL to shorten.',
    requiredCode: 'Enter a short code to look up.',
  },
}
