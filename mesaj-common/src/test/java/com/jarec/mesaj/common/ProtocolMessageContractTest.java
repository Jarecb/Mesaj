package com.jarec.mesaj.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarec.mesaj.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Verifies protocol JSON round trips and binary frame encoding and decoding.
 */
class ProtocolMessageContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAndDeserializesEveryProtocolMessage() throws Exception {
        var messages = List.of(
                new Login("alex"),
                new LoginSuccess("alex", "user-1", "session-1"),
                new GetUsers(),
                new UserList(Map.of("alex", "user-1", "maria", "user-2")),
                new SendMessage("user-2", "msg-1", "hello world"),
                new MessageReceived("msg-1", "user-2", 9L),
                new BatchDelivery(List.of("msg-1", "msg-2"), "user-2", "user-1"),
                new AckBatch("user-2", List.of("msg-1", "msg-2")),
                new ReadReceiptUpdate("user-1", "user-2", "msg-1", true)
        );

        for (ProtocolMessage message : messages) {
            String json = mapper.writeValueAsString(message);
            ProtocolMessage roundTrip = mapper.readValue(json, ProtocolMessage.class);
            assertThat(roundTrip).isEqualTo(message);
        }
    }

    @Test
    void packetCodecHandlesFrameLengthAndPayloadRules() throws Exception {
        byte[] encoded = PacketEncoder.encode(new LoginSuccess("alex", "user-1", "session-1"));

        ProtocolMessage decoded = PacketDecoder.decode(new ByteArrayInputStream(encoded));
        assertThat(decoded).isEqualTo(new LoginSuccess("alex", "user-1", "session-1"));

        assertThatThrownBy(() -> PacketDecoder.decode(new ByteArrayInputStream(new byte[] {0, 0, 0, 0})))
                .isInstanceOf(Exception.class);

        byte[] oversized = new byte[PacketDecoder.MAX_FRAME_SIZE + 4];
        oversized[0] = 0x00;
        oversized[1] = 0x00;
        oversized[2] = (byte) (PacketDecoder.MAX_FRAME_SIZE >>> 8);
        oversized[3] = (byte) PacketDecoder.MAX_FRAME_SIZE;
        assertThatThrownBy(() -> PacketDecoder.decode(new ByteArrayInputStream(oversized)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsMessageAcknowledgmentsWithoutAValidSequence() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MessageReceived("msg-1", "user-1", 0));
    }
}
