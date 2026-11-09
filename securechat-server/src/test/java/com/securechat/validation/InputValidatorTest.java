package com.securechat.validation;

import com.securechat.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void normalizeHistoryLimitBoundaries() {
        // Fallback to default when <= 0
        assertEquals(30, InputValidator.normalizeHistoryLimit(0));
        assertEquals(30, InputValidator.normalizeHistoryLimit(-1));
        assertEquals(30, InputValidator.normalizeHistoryLimit(-100));
        assertEquals(30, InputValidator.normalizeHistoryLimit(Integer.MIN_VALUE));

        // Values within [1, 100] preserved
        assertEquals(1, InputValidator.normalizeHistoryLimit(1));
        assertEquals(30, InputValidator.normalizeHistoryLimit(30));
        assertEquals(50, InputValidator.normalizeHistoryLimit(50));
        assertEquals(100, InputValidator.normalizeHistoryLimit(100));

        // Values above 100 clipped to 100
        assertEquals(100, InputValidator.normalizeHistoryLimit(101));
        assertEquals(100, InputValidator.normalizeHistoryLimit(150));
        assertEquals(100, InputValidator.normalizeHistoryLimit(Integer.MAX_VALUE));
    }

    @Test
    void validateMessageContentRejectsBlankAndTooLong() {
        assertThrows(BusinessException.class, () -> InputValidator.validateMessageContent(null));
        assertThrows(BusinessException.class, () -> InputValidator.validateMessageContent(""));
        assertThrows(BusinessException.class, () -> InputValidator.validateMessageContent("   "));
        assertThrows(BusinessException.class,
                () -> InputValidator.validateMessageContent("a".repeat(4001)));
        assertEquals("hello", InputValidator.validateMessageContent("  hello  "));
    }

    @Test
    void validateGroupNameRejectsBlankAndTooLong() {
        assertThrows(BusinessException.class, () -> InputValidator.validateGroupName(null));
        assertThrows(BusinessException.class, () -> InputValidator.validateGroupName(""));
        assertThrows(BusinessException.class, () -> InputValidator.validateGroupName("   "));
        assertThrows(BusinessException.class,
                () -> InputValidator.validateGroupName("a".repeat(101)));
        assertEquals("Team A", InputValidator.validateGroupName("  Team A  "));
    }
}