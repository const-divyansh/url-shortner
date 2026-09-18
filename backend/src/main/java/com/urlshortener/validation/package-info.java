/**
 * Inbound request validation.
 *
 * <p>Pattern: Chain of Responsibility. Each rule (URL well-formedness, scheme
 * allow-list, private-IP/SSRF block, alias charset and length) is a separate
 * link, so rules can be added or reordered without editing a single growing
 * validator method.
 *
 * <p>Covers FR7 and the "validate and normalize all external input" rule.
 */
package com.urlshortener.validation;
