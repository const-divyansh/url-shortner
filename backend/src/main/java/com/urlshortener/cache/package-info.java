/**
 * Redis caching for the redirect hot path.
 *
 * <p>Pattern: Cache-Aside. Reads consult Redis first and fall back to
 * PostgreSQL on a miss.
 *
 * <p>Reliability rule (NFR3): a Redis outage must degrade to a database lookup,
 * never fail the redirect. Cache failures are handled here, not propagated to
 * callers.
 */
package com.urlshortener.cache;
