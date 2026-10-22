package com.securechat.exception;

/**
 * Business-level error with a stable error code delivered to client via Packet.error().
 * Unchecked — ClientHandler catches it and maps code → response.
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}