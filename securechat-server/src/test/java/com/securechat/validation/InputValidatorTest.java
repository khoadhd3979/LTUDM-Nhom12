package com.securechat.validation;

import com.securechat.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
        assertEquals(30, InputValidator.normalizeHistoryLimit(0));
        assertEquals(30, InputValidator.normalizeHistoryLimit(-1));
        assertEquals(30, InputValidator.normalizeHistoryLimit(-100));
        assertEquals(30, InputValidator.normalizeHistoryLimit(Integer.MIN_VALUE));
        assertEquals(1, InputValidator.normalizeHistoryLimit(1));
        assertEquals(30, InputValidator.normalizeHistoryLimit(30));
        assertEquals(50, InputValidator.normalizeHistoryLimit(50));
        assertEquals(100, InputValidator.normalizeHistoryLimit(100));
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

    @Test
    void validateMsgTypeBoundaries() {
        assertEquals("TEXT", InputValidator.validateMsgType(null));
        assertEquals("TEXT", InputValidator.validateMsgType(""));
        assertEquals("TEXT", InputValidator.validateMsgType("   "));
        assertEquals("TEXT", InputValidator.validateMsgType("text"));
        assertEquals("TEXT", InputValidator.validateMsgType("TEXT"));
        assertEquals("STICKER", InputValidator.validateMsgType("sticker"));
        assertEquals("STICKER", InputValidator.validateMsgType("STICKER"));
        assertEquals("STICKER", InputValidator.validateMsgType("  Sticker  "));
        assertThrows(BusinessException.class, () -> InputValidator.validateMsgType("FILE"));
        assertThrows(BusinessException.class, () -> InputValidator.validateMsgType("VIDEO"));
        assertThrows(BusinessException.class, () -> InputValidator.validateMsgType("AUDIO"));
        assertThrows(BusinessException.class, () -> InputValidator.validateMsgType("text;DROP"));
    }

    // ============================================================
    // [M4-B] FILE_CHUNK validation
    // ============================================================

    @Test
    void validateFileChunk_HappyPath_ReturnsNormalizedMap() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "user@test.local");
        Map<String, Object> out = InputValidator.validateFileChunk(p);
        assertEquals("file-1", out.get("fileId"));
        assertEquals("chunk.txt", out.get("fileName"));
        assertEquals(0, out.get("chunkIndex"));
        assertEquals(3, out.get("totalChunks"));
        assertEquals(p.get("data"), out.get("data"));
        assertEquals("user@test.local", out.get("receiver"));
        assertFalse(out.containsKey("groupName"));
    }

    @Test
    void validateFileChunk_HappyPath_GroupVariant() {
        Map<String, Object> p = validChunk();
        p.put("groupName", "Team Alpha");
        Map<String, Object> out = InputValidator.validateFileChunk(p);
        assertEquals("Team Alpha", out.get("groupName"));
        assertFalse(out.containsKey("receiver"));
    }

    @Test
    void validateFileChunk_RejectsMissingFileId() {
        Map<String, Object> p = validChunk();
        p.remove("fileId");
        p.put("receiver", "x@y.z");
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsBlankFileName() {
        Map<String, Object> p = validChunk();
        p.put("fileName", "   ");
        p.put("receiver", "x@y.z");
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsTotalChunksOutOfBounds() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "x@y.z");
        p.put("totalChunks", 0);
        p.put("chunkIndex", 0);
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));

        p.put("totalChunks", 129);
        p.put("chunkIndex", 0);
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsChunkIndexOutOfRange() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "x@y.z");
        p.put("totalChunks", 3);
        p.put("chunkIndex", -1);
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));

        p.put("chunkIndex", 3);
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsInvalidBase64() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "x@y.z");
        p.put("data", "not!base64@@");
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsEmptyDecodedData() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "x@y.z");
        p.put("data", "");   // empty string
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsDataTooLarge() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "x@y.z");
        byte[] big = new byte[262_145];   // 1 byte over limit
        p.put("data", Base64.getEncoder().encodeToString(big));
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsBothRoutingFields() {
        Map<String, Object> p = validChunk();
        p.put("receiver", "a@b.c");
        p.put("groupName", "Group A");
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    @Test
    void validateFileChunk_RejectsNoRoutingField() {
        Map<String, Object> p = validChunk();   // no receiver, no groupName
        assertThrows(BusinessException.class, () -> InputValidator.validateFileChunk(p));
    }

    private static Map<String, Object> validChunk() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileId", "file-1");
        m.put("fileName", "chunk.txt");
        m.put("chunkIndex", 0);
        m.put("totalChunks", 3);
        m.put("data", Base64.getEncoder().encodeToString("Hello".getBytes()));
        return m;
    }
}