package com.securechat.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolTest {

    @Test
    public void testPacketSerialization() throws Exception {
        Packet req = Packet.request(MessageType.PING, "req-1", Map.of("data", "hello"));
        String json = ProtocolSerializer.toJson(req);
        assertNotNull(json);

        Packet deserialized = ProtocolSerializer.fromJson(json);
        assertEquals(MessageType.PING, deserialized.getType());
        assertEquals("req-1", deserialized.getRequestId());
        assertEquals("hello", deserialized.getPayload().get("data"));
    }

    @Test
    public void testOkAndErrorPackets() {
        Packet ok = Packet.ok(MessageType.LOGIN, "req-2", Map.of("userId", 100));
        assertEquals("OK", ok.getStatus());

        Packet err = Packet.error("req-3", "AUTH_FAILED");
        assertEquals("ERROR", err.getStatus());
        assertEquals("AUTH_FAILED", err.getError());
    }
}
