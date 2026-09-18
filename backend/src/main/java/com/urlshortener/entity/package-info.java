/**
 * JPA entities - the persistent domain model (PostgreSQL is the source of
 * truth, NFR9).
 *
 * <p>Entities stay internal to the application: they are mapped to {@code dto}
 * types before crossing the HTTP boundary, so storage layout and API contract
 * can evolve independently.
 */
package com.urlshortener.entity;
