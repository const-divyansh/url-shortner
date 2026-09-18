import { execFileSync } from 'node:child_process'
import { expect, test, type Locator, type Page } from '@playwright/test'

const API_BASE_URL = process.env.PLAYWRIGHT_API_URL ?? 'http://localhost:8080'
const SHORT_CODE_PATTERN = /^[A-Za-z0-9_-]{3,32}$/
const CREATE_ENDPOINT = `${API_BASE_URL}/api/urls`
const GUEST_SESSION_ENDPOINT = `${API_BASE_URL}/api/auth/session`
const DEFAULT_TARGET_HOST = 'https://example.com'
const ALTERNATE_TARGET_HOST = 'https://example.org'
const REPO_ROOT = `${process.cwd()}/..`

test.describe.configure({ mode: 'serial' })

interface CreateFormInput {
  targetUrl: string
  customAlias?: string
  expiresAtLocal?: string
}

interface CreatedLink {
  shortCode: string
  shortUrl: string
  targetUrl: string
  expiresAt?: string
}

function uniqueSuffix(): string {
  return `${Date.now()}${Math.random().toString(36).slice(2, 8)}`
}

function uniqueAlias(prefix: string): string {
  const raw = `${prefix}${uniqueSuffix()}`.toLowerCase().replace(/[^a-z0-9_-]/g, '')

  return raw.slice(0, 32)
}

function uniqueTargetUrl(prefix: string): string {
  return `${DEFAULT_TARGET_HOST}/${prefix}/${uniqueSuffix()}`
}

function alternateTargetUrl(prefix: string): string {
  return `${ALTERNATE_TARGET_HOST}/${prefix}/${uniqueSuffix()}`
}

function nextMinuteLocalInput(): string {
  const date = new Date(Date.now() + 61_000)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function ownedLinkCard(page: Page, shortCode: string): Locator {
  return page.locator('.owned-link-card').filter({ hasText: shortCode })
}

function clearCreateRateLimitState(): void {
  execFileSync(
    'docker',
    [
      'compose',
      'exec',
      '-T',
      'redis',
      'redis-cli',
      '--raw',
      'EVAL',
      "local keys = redis.call('keys', ARGV[1]); if #keys > 0 then return redis.call('del', unpack(keys)) end; return 0",
      '0',
      'ratelimit:v1:*',
    ],
    {
      cwd: REPO_ROOT,
      stdio: 'ignore',
    },
  )
}

async function signInAsGuest(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByRole('button', { name: 'Continue as guest' }).click()
  await expect(page.getByRole('heading', { name: 'Shorten a link' })).toBeVisible()
}

async function setExpiryInput(page: Page, value: string): Promise<void> {
  const toggle = page.getByLabel('Set an expiry date')
  await toggle.check()

  const expiryInput = page.locator('#expiresAt')
  await expect(expiryInput).toBeVisible()
  await expiryInput.fill(value)
}

async function submitCreateForm(page: Page, input: CreateFormInput) {
  await page.getByLabel('Long URL').fill(input.targetUrl)

  const aliasInput = page.getByLabel(/Custom alias/)
  if (input.customAlias) {
    await aliasInput.fill(input.customAlias)
  } else {
    await aliasInput.fill('')
  }

  if (input.expiresAtLocal) {
    await setExpiryInput(page, input.expiresAtLocal)
  }

  const responsePromise = page.waitForResponse(
    (response) =>
      response.url() === CREATE_ENDPOINT && response.request().method() === 'POST',
  )

  await page.locator('form').getByRole('button', { name: 'Shorten' }).click()

  return responsePromise
}

async function createLink(page: Page, input: CreateFormInput): Promise<CreatedLink> {
  const response = await submitCreateForm(page, input)
  expect(response.status()).toBe(201)

  const created = (await response.json()) as CreatedLink
  await expect(page.getByRole('heading', { name: 'Your short link' })).toBeVisible()

  return created
}

async function resetCreateForm(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Shorten another' }).click()
  await expect(page.getByRole('heading', { name: 'Shorten a link' })).toBeVisible()
}

async function openAnalyticsForCreatedLink(page: Page, shortCode: string): Promise<void> {
  await page.getByRole('button', { name: 'View analytics' }).click()
  await expect(page.getByRole('heading', { name: 'Link analytics' })).toBeVisible()
  await expect(page.getByLabel('Short code')).toHaveValue(shortCode)
}

function totalClicksValue(page: Page): Locator {
  return page.locator('.stat').filter({ hasText: 'Total clicks' }).locator('.stat-value')
}

test.beforeEach(() => {
  clearCreateRateLimitState()
})

test('creates a short URL with an auto-generated code and displays it', async ({ page }) => {
  await signInAsGuest(page)

  const created = await createLink(page, {
    targetUrl: uniqueTargetUrl('generated'),
  })

  expect(created.shortCode).toMatch(SHORT_CODE_PATTERN)
  const link = page.locator('.result-url a')
  await expect(link).toHaveAttribute('href', created.shortUrl)
  await expect(link).toHaveText(created.shortUrl)
})

test('creates a short URL with a custom alias and rejects reusing it', async ({ page }) => {
  await signInAsGuest(page)

  const alias = uniqueAlias('alias')
  const created = await createLink(page, {
    targetUrl: uniqueTargetUrl('custom-alias'),
    customAlias: alias,
  })

  expect(created.shortCode).toBe(alias)
  await expect(page.locator('.result-url a')).toHaveAttribute('href', `${API_BASE_URL}/${alias}`)
  await resetCreateForm(page)

  const response = await submitCreateForm(page, {
    targetUrl: alternateTargetUrl('duplicate-second'),
    customAlias: alias,
  })

  expect(response.status()).toBe(409)
  const error = (await response.json()) as { code: string; message: string }
  expect(error.code).toBe('alias.unavailable')
  await expect(page.locator('.error')).toContainText(error.message)
})

test('rejects an invalid URL through the UI with a 400 response', async ({ page }) => {
  await signInAsGuest(page)

  const response = await submitCreateForm(page, {
    targetUrl: 'not-a-valid-url',
  })

  expect(response.status()).toBe(400)
  const error = (await response.json()) as { code: string; message: string }
  expect(error.code).toBe('url.not_absolute')
  await expect(page.locator('.error')).toContainText(error.message)
})

test('establishes a guest session, hides delete controls, and updates analytics after a redirect', async ({ page }) => {
  await signInAsGuest(page)

  const targetUrl = uniqueTargetUrl('analytics')
  const created = await createLink(page, { targetUrl })

  const sessionResponsePromise = page.waitForResponse(
    (response) =>
      response.url() === GUEST_SESSION_ENDPOINT && response.request().method() === 'GET',
  )
  await openAnalyticsForCreatedLink(page, created.shortCode)

  const sessionResponse = await sessionResponsePromise
  expect(sessionResponse.status()).toBe(200)
  const session = (await sessionResponse.json()) as { guest: boolean; provider: string }
  expect(session.guest).toBe(true)
  expect(session.provider).toBeTruthy()

  await expect(ownedLinkCard(page, created.shortCode)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Delete' })).toHaveCount(0)

  await expect(totalClicksValue(page)).toHaveText('0')

  const redirectResponse = await page.request.get(created.shortUrl, {
    maxRedirects: 0,
    failOnStatusCode: false,
  })
  expect(redirectResponse.status()).toBe(302)
  expect(redirectResponse.headers().location).toBe(targetUrl)

  await expect(totalClicksValue(page)).toHaveText('1')
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await expect(ownedLinkCard(page, created.shortCode)).toContainText('1 click')
})

test('returns 410 once an expiring link has passed its expiry time', async ({ page }) => {
  test.slow()
  await signInAsGuest(page)

  const created = await createLink(page, {
    targetUrl: uniqueTargetUrl('expires'),
    expiresAtLocal: nextMinuteLocalInput(),
  })

  expect(created.expiresAt).toBeTruthy()
  const expiresAtMs = Date.parse(created.expiresAt as string)
  const waitMs = Math.max(expiresAtMs - Date.now() + 1_500, 0)
  await page.waitForTimeout(waitMs)

  const expiredResponse = await page.request.get(created.shortUrl, {
    maxRedirects: 0,
    failOnStatusCode: false,
  })
  expect(expiredResponse.status()).toBe(410)
})

test('rate limits rapid create requests with a 429 response', async ({ page }) => {
  await signInAsGuest(page)

  let limited = false

  for (let attempt = 0; attempt < 20; attempt += 1) {
    const response = await submitCreateForm(page, {
      targetUrl: uniqueTargetUrl(`rate-limit-${attempt}`),
      customAlias: uniqueAlias(`rl${attempt}`),
    })

    if (response.status() === 429) {
      limited = true
      const error = (await response.json()) as { code: string; message: string }
      expect(error.code).toBe('rate_limit.exceeded')
      expect(response.headers()['retry-after']).toBeTruthy()
      await expect(page.locator('.error')).toContainText(error.message)
      break
    }

    expect(response.status()).toBe(201)
    await resetCreateForm(page)
  }

  expect(limited).toBe(true)
})
