/**
 * Persistence access via Spring Data JPA.
 *
 * <p>Pattern: Repository. Isolates the rest of the application from JPA and
 * from PostgreSQL specifics.
 *
 * <p>Security rule: JPA/parameterized queries only - no raw SQL string
 * concatenation anywhere in this package.
 *
 * <p>Depends on: {@code entity}.
 */
package com.urlshortener.repository;
