package me.almana.logisticsnetworks.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModifierKeysTest {
    @AfterEach
    void clearState() {
        ServerPayloadHandler.clearModifierKeys();
    }

    @Test
    void masksModifierStateToThreeBits() {
        UUID playerId = UUID.randomUUID();

        ServerPayloadHandler.setModifierKeys(playerId, 0b1111);

        assertTrue(ServerPayloadHandler.isModifierDown(playerId, 0));
        assertTrue(ServerPayloadHandler.isModifierDown(playerId, 1));
        assertTrue(ServerPayloadHandler.isModifierDown(playerId, 2));
        assertFalse(ServerPayloadHandler.isModifierDown(playerId, 3));
        assertFalse(ServerPayloadHandler.isModifierDown(playerId, -1));
    }

    @Test
    void clearsOnePlayerWithoutClearingOthers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ServerPayloadHandler.setModifierKeys(first, 1);
        ServerPayloadHandler.setModifierKeys(second, 2);

        ServerPayloadHandler.clearModifierKeys(first);

        assertFalse(ServerPayloadHandler.isModifierDown(first, 0));
        assertTrue(ServerPayloadHandler.isModifierDown(second, 1));
    }

    @Test
    void payloadUsesExactIdAndUnsignedByteCodec() {
        assertEquals(Identifier.fromNamespaceAndPath("logisticsnetworks", "sync_modifier_keys"),
                SyncModifierKeysPayload.TYPE.id());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncModifierKeysPayload.STREAM_CODEC.encode(buffer, new SyncModifierKeysPayload(0xff));
            assertEquals(255, SyncModifierKeysPayload.STREAM_CODEC.decode(buffer).mask());
        } finally {
            buffer.release();
        }
    }
}
