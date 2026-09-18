-- Schema for the URL shortener (ADR-008: plain schema.sql, no migration framework).
--
-- This file re-runs on every startup against a persistent volume, so every statement
-- must be idempotent. Future columns must be added as separate
-- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statements below - editing the CREATE
-- TABLE has no effect once the table exists.

CREATE TABLE IF NOT EXISTS urls (
    -- Internal surrogate key. Never exposed: ADR-007 rejects enumerable public
    -- identifiers. Exists so the click_events table can reference an 8-byte FK rather
    -- than repeating the short code on every click row.
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- The public identifier. Generated codes and custom aliases share this single
    -- column: they occupy one redirect namespace, so one UNIQUE constraint lets
    -- Postgres arbitrate uniqueness. Splitting them across two tables would make
    -- uniqueness unenforceable by the database and force a check-then-act race in
    -- application code.
    --
    -- The constraint is named explicitly because the service distinguishes a genuine
    -- short-code collision (retry) from any other integrity violation (a bug) by
    -- matching on this name.
    short_code  VARCHAR(32)   NOT NULL,

    target_url  VARCHAR(2048) NOT NULL,

    -- TIMESTAMPTZ, never TIMESTAMP: the database runs UTC while clients and
    -- developers do not. A naive timestamp would silently drop the offset.
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- NULL means the link never expires. Immutable once set (see requirements.md,
    -- Design constraints) so expiry stays a property carried inside the cached value
    -- rather than an event needing cache invalidation.
    expires_at  TIMESTAMPTZ,

    CONSTRAINT urls_short_code_key UNIQUE (short_code),

    -- Rejects characters that would be ambiguous or unsafe in a URL path. Defence in
    -- depth: application validation already enforces this, but the database is the
    -- component that cannot be bypassed.
    CONSTRAINT urls_short_code_charset CHECK (short_code ~ '^[A-Za-z0-9_-]+$'),

    CONSTRAINT urls_target_url_not_blank CHECK (length(btrim(target_url)) > 0)
);

-- Identity: who the owner is, independent of how many session tokens they hold.
-- One row per real-world caller. 'guest' owners have no external_subject/email;
-- 'google' owners are keyed by Google's stable `sub` claim.
CREATE TABLE IF NOT EXISTS owners (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider          VARCHAR(32)   NOT NULL,
    external_subject  VARCHAR(255),
    email             VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT owners_provider_not_blank CHECK (length(btrim(provider)) > 0),

    -- NULLs are not considered equal by a UNIQUE constraint, so any number of guest
    -- rows (external_subject NULL) coexist; only a given provider+subject pair
    -- (e.g. two logins from the same Google account) is deduplicated.
    CONSTRAINT owners_provider_subject_key UNIQUE (provider, external_subject)
);

-- Credential: one opaque, revocable session token per issued login. Split from
-- owners so a single owner can hold multiple valid sessions (e.g. two browsers)
-- and one session can be revoked (delete the row) without touching the identity.
CREATE TABLE IF NOT EXISTS owner_sessions (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    BIGINT        NOT NULL REFERENCES owners(id),
    token_hash  VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT owner_sessions_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_owner_sessions_owner
    ON owner_sessions (owner_id);

ALTER TABLE urls
    ADD COLUMN IF NOT EXISTS owner_id BIGINT;

-- Intentionally indexed but not constrained here. Spring's SQL initializer executes
-- one statement at a time and does not handle the conditional PL/pgSQL block that an
-- idempotent named FK would require. Ownership is therefore enforced in application
-- code for now; if migrations are introduced later, add the FK there.
CREATE INDEX IF NOT EXISTS idx_urls_owner_created
    ON urls (owner_id, created_at DESC);

-- No explicit index on short_code: PostgreSQL implements UNIQUE with a B-tree index,
-- so urls_short_code_key already serves the redirect lookup, which is the hot path.

-- Click analytics (FR5). Added in M4.
--
-- Written asynchronously by the drainer, never on the redirect request thread, so a
-- slow or failed analytics write cannot affect a redirect.
CREATE TABLE IF NOT EXISTS click_events (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- References the internal surrogate key rather than repeating the short code on
    -- every row: this is by far the largest table, one row per click.
    url_id      BIGINT       NOT NULL REFERENCES urls(id),

    occurred_at TIMESTAMPTZ  NOT NULL,

    ip_hash     VARCHAR(64),

    -- Truncated on write: these are caller-supplied headers of unbounded length.
    referrer    VARCHAR(2048),
    user_agent  VARCHAR(512)
);

-- Supports both analytics queries: counting clicks for a URL, and listing its most
-- recent clicks. The DESC ordering matches the paginated history query so the index
-- satisfies it without a sort.
CREATE INDEX IF NOT EXISTS idx_click_events_url_occurred
    ON click_events (url_id, occurred_at DESC);
