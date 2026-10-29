package com.securechat.validation;

import com.securechat.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValidatorTest {

    @Test
    void passwordLimitUsesUtf8Bytes() {
        assertDoesNotThrow(() -> InputValidator.validatePassword("a".repeat(72)));
        assertThrows(BusinessException.class,
                () -> InputValidator.validatePassword("a".repeat(73)));
        assertDoesNotThrow(() -> InputValidator.validatePassword("ế".repeat(24)));
        assertThrows(BusinessException.class,
                () -> InputValidator.validatePassword("ế".repeat(25)));
    }
}