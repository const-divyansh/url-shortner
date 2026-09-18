/**
 * HTTP entry points (Spring MVC controllers).
 *
 * <p>Responsibility: translate HTTP to/from {@code dto} types and delegate to
 * {@code service}. Controllers hold no business logic and never touch
 * {@code repository}, {@code cache}, or {@code entity} directly.
 *
 * <p>Depends on: {@code service}, {@code dto}.
 */
package com.urlshortener.controller;
