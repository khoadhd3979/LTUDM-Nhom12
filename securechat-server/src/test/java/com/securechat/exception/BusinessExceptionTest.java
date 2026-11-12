package com.securechat.exception;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void oldConstructorProducesEmptyDetails() {
        BusinessException ex = new BusinessException("CODE_X", "msg");
        assertEquals("CODE_X", ex.getCode());
        assertEquals("msg", ex.getMessage());
        assertNotNull(ex.getDetails());
        assertTrue(ex.getDetails().isEmpty());
    }

    @Test
    void newConstructorCarriesDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("groupIds", List.of(1L, 2L));
        BusinessException ex = new BusinessException(
                "CANNOT_LOCK_LAST_ADMIN_OF_GROUP", "blocked", details);
        assertEquals("CANNOT_LOCK_LAST_ADMIN_OF_GROUP", ex.getCode());
        assertEquals("blocked", ex.getMessage());
        assertEquals(List.of(1L, 2L), ex.getDetails().get("groupIds"));

        // null + empty maps become empty
        assertTrue(new BusinessException("C", "m", null).getDetails().isEmpty());
        assertTrue(new BusinessException("C", "m", Map.of()).getDetails().isEmpty());
    }

    @Test
    void detailsIsUnmodifiable() {
        BusinessException ex = new BusinessException("CODE", "msg", Map.of("k", "v"));
        Map<String, Object> d = ex.getDetails();
        assertThrows(UnsupportedOperationException.class, () -> d.put("new", "val"));
    }

    @Test
    void detailsIsDefensiveCopyOfInputMap() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("k", "v");
        BusinessException ex = new BusinessException("CODE", "msg", source);

        // mutate source after construction
        source.put("k", "modified");
        source.put("extra", "added");

        assertEquals("v", ex.getDetails().get("k"));
        assertNull(ex.getDetails().get("extra"));
    }
}