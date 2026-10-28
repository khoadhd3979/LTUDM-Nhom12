package com.securechat.common.protocol;

import java.util.HashMap;
import java.util.Map;

public class Packet {
    private String type;
    private String requestId;
    private Map<String, Object> payload = new HashMap<>();
    private String status;
    private String error;

    public Packet() {}

    public static Packet request(String type, String requestId, Map<String, Object> payload) {
        Packet p = new Packet();
        p.type = type; p.requestId = requestId; p.payload = payload;
        return p;
    }
    public static Packet ok(String type, String requestId, Map<String, Object> payload) {
        Packet p = request(type, requestId, payload);
        p.status = "OK";
        return p;
    }
    public static Packet error(String requestId, String code) {
        Packet p = new Packet();
        p.type = "ERROR"; p.requestId = requestId;
        p.status = "ERROR"; p.error = code;
        return p;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) {
        this.payload = (payload == null) ? new HashMap<>() : payload;
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}