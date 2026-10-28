package com.securechat.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ProtocolSerializer {
    private static final ObjectMapper M = new ObjectMapper();

    private ProtocolSerializer() {}

    public static String toJson(Packet p) throws JsonProcessingException {
        return M.writeValueAsString(p);
    }
    public static Packet fromJson(String s) throws JsonProcessingException {
        return M.readValue(s, Packet.class);
    }
}