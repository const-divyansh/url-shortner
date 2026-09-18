/**
 * Spring configuration and typed configuration properties.
 *
 * <p>Holds wiring only - bean definitions, Redis/serialization setup, async
 * executor configuration, and rate-limit settings.
 *
 * <p>Security rule: configuration values come from the environment. No secrets
 * are hardcoded in this package or in the bundled YAML.
 */
package com.urlshortener.config;
