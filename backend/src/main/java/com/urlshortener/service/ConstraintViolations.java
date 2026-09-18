package com.urlshortener.service;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifies which database constraint a failed write violated.
 *
 * <p>Exists because {@link DataIntegrityViolationException} is raised for every
 * integrity failure - a short-code collision, a charset violation, a future foreign
 * key. Only the first is worth retrying; retrying the others would burn the retry
 * budget and report a generation failure while concealing the real defect.
 *
 * <p>The constraint is matched by name, which is why {@code schema.sql} names it
 * explicitly rather than letting PostgreSQL choose.
 */
final class ConstraintViolations {

    /**
     * Must match the constraint name in {@code schema.sql}.
     */
    static final String SHORT_CODE_UNIQUE = "urls_short_code_key";

    private ConstraintViolations() {
    }

    static boolean isShortCodeCollision(DataIntegrityViolationException e) {
        return mentions(e);
    }

    /**
     * Walks the cause chain looking for the constraint name.
     *
     * <p>Hibernate exposes it via {@code ConstraintViolationException#getConstraintName},
     * but that type is not on to compile classpath of this package and the wrapping
     * differs between drivers and versions. Scanning the chain's messages is
     * deliberately conservative: a false negative merely means the code is not retried
     * and the caller sees an error, which is safer than a false positive silently
     * retrying an unrelated failure.
     */
    private static boolean mentions(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(ConstraintViolations.SHORT_CODE_UNIQUE)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
