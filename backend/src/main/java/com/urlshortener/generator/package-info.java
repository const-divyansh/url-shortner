/**
 * Short-code generation algorithms.
 *
 * <p>Pattern: Strategy (with Factory Method for bean selection). The
 * {@code ShortCodeGenerator} abstraction lets the algorithm change without
 * modifying the service that consumes it - the Open/Closed and Dependency
 * Inversion principles applied to the one part of this system most likely to
 * be swapped.
 *
 * <p>Default strategy (see ADR-007): random Base62 with collision-retry.
 */
package com.urlshortener.generator;
