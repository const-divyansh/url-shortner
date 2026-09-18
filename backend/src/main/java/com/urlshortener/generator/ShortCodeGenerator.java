package com.urlshortener.generator;

/**
 * Strategy for producing short codes.
 *
 * <p><strong>Pattern: Strategy.</strong> Short-code generation is the part of this
 * system most likely to change, so it sits behind an abstraction rather than inside
 * the service. New algorithms arrive as new implementations; no existing class is
 * edited (Open/Closed), and the service depends on this interface rather than on any
 * concrete algorithm (Dependency Inversion).
 *
 * <p><strong>Contract all implementations must honour</strong> - callers rely on these
 * regardless of which strategy is active (Liskov Substitution):
 * <ol>
 *   <li>Returns a non-null, non-blank string of {@link #CODE_LENGTH} characters drawn
 *       only from the Base62 alphabet.</li>
 *   <li>Never blocks and never performs I/O. Uniqueness is enforced by the database's
 *       unique constraint, not here - a generator proposes, the database disposes.</li>
 *   <li>Given a strictly greater {@code attempt}, a deterministic implementation must
 *       return a different code. Without this, the caller's retry loop cannot make
 *       progress.</li>
 * </ol>
 *
 * <p>Implementations are singletons shared across request threads and must be
 * thread-safe.
 */
public interface ShortCodeGenerator {

    /**
     * Length of every generated code.
     *
     * <p>Seven per ADR-007: {@code 62^7} is roughly 3.5 trillion codes, which keeps
     * random collisions negligible while staying short enough to be shareable.
     */
    int CODE_LENGTH = 7;

    /**
     * Stable identifier used to select this strategy by configuration or at runtime.
     * Must be unique across implementations; duplicates fail fast at startup.
     */
    String name();

    /**
     * Produces a candidate short code. The result is a proposal, not a reservation -
     * the caller attempts the insert and retries with an incremented attempt on
     * collision.
     */
    String generate(GenerationContext context);
}
