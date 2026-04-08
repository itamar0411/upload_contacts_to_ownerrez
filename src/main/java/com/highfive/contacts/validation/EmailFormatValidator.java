package com.highfive.contacts.validation;

import org.apache.commons.validator.routines.EmailValidator;

import java.util.regex.Pattern;

/**
 * Two-layer email format validation.
 * Layer 1: fast regex pre-check.
 * Layer 2: Apache Commons Validator RFC-compliant check.
 */
public class EmailFormatValidator {

    private static final Pattern BASIC_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final EmailValidator COMMONS_VALIDATOR = EmailValidator.getInstance();

    public record Result(boolean valid, String reason) {}

    public Result validate(String email) {
        if (email == null || email.isBlank()) {
            return new Result(false, "Empty email");
        }
        if (!BASIC_PATTERN.matcher(email).matches()) {
            return new Result(false, "Failed basic format check (missing @, domain, or TLD)");
        }
        if (!COMMONS_VALIDATOR.isValid(email)) {
            return new Result(false, "Failed RFC email validation");
        }
        return new Result(true, "");
    }
}
