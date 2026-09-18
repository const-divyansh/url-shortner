package com.urlshortener.validation.rule;

/**
 * Ordering of the validation chain.
 *
 * <p>Rules run cheapest-first so that malformed input is rejected before anything
 * expensive runs. Syntax must come first because later rules parse the URL and would
 * otherwise fail on unparseable input; DNS resolution comes last because it is the
 * only rule performing I/O.
 */
public final class RuleOrder {

    public static final int SYNTAX = 10;
    public static final int LENGTH = 20;
    public static final int SCHEME = 30;
    public static final int ALIAS_FORMAT = 40;
    public static final int RESERVED_ALIAS = 50;
    public static final int EXPIRY = 60;
    public static final int HOST_SAFETY = 70;

    private RuleOrder() {
    }
}
