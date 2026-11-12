package com.securechat.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business-level error with a stable error code delivered to client via Packet.error().
 * Unchecked — ClientHandler catches it and maps code → response.
 *
 * [M3-B] Additive extension: optional structured details map accessible via getDetails().
 * Old two-arg constructor still works; details = empty map.
 * Details is stored as an unmodifiable defensive copy of the caller's map.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.details = Collections.emptyMap();
    }

    public BusinessException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        if (details == null || details.isEmpty()) {
            this.details = Collections.emptyMap();
        } else {
            this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }
    }

    public String getCode() { return code; }

    public Map<String, Object> getDetails() { return details; }
}