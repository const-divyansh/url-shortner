package com.urlshortener.dto;

import java.util.Set;

/**
 * The short-code generation strategy currently in force, and the ones available.
 *
 * <p>Exposed so the client can show which algorithm produced a code rather than leaving
 * it as invisible server state (ADR-007 ships two strategies precisely so the difference
 * is demonstrable).
 */
public record ShortCodeStrategyResponse(String active, Set<String> available) {
}
