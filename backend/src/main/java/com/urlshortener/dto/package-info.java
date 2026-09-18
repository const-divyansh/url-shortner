/**
 * Request and response payloads that define the public API contract.
 *
 * <p>Kept separate from {@code entity} so that persistence changes do not leak
 * into the API, and so that internal fields are never serialised to clients by
 * accident.
 */
package com.urlshortener.dto;
