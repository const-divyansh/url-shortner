package com.urlshortener.dto;

/**
 * Request to change the active short-code generation strategy.
 */
public record UpdateShortCodeStrategyRequest(String strategy) {
}
