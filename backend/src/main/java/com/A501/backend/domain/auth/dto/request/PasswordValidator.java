package com.A501.backend.domain.auth.dto.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final String ALLOWED_SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : value.toCharArray()) {
            if (isAsciiLetter(c)) {
                hasLetter = true;
            } else if (isAsciiDigit(c)) {
                hasDigit = true;
            } else if (ALLOWED_SPECIAL_CHARS.indexOf(c) >= 0) {
                hasSpecial = true;
            } else {
                return false;
            }
        }

        return hasLetter && hasDigit && hasSpecial;
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
