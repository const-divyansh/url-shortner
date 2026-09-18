/**
 * Application exceptions and the centralised HTTP error mapping.
 *
 * <p>A single {@code @ControllerAdvice} translates exceptions into one
 * consistent JSON error format (M5), so error shape is defined once rather
 * than per controller.
 *
 * <p>Error responses must not leak internal details such as stack traces,
 * SQL, or infrastructure hostnames.
 */
package com.urlshortener.exception;
