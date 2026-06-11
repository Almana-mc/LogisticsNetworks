package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncMassPlacementChoicesPayload(
        int containerId,
        List<BlockChoice> choices,
        int maxNodes) implements CustomPacketPayload {

    public record BlockChoice(Identifier blockId, String name, int targetCount, boolean selected) {
    }

    public static final CustomPacketPayload.Type<SyncMassPlacementChoicesPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_mass_placement_choices"));

    public static final StreamCodec<FriendlyByteBuf, SyncMassPlacementChoicesPayload> STREAM_CODEC = StreamCodec
            .of(SyncMassPlacementChoicesPayload::write, SyncMassPlacementChoicesPayload::read);

    public static SyncMassPlacementChoicesPayload read(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int count = buf.readVarInt();
        List<BlockChoice> choices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Identifier blockId = Identifier.tryParse(buf.readUtf(128));
            String name = buf.readUtf(1024);
            int targetCount = buf.readVarInt();
            boolean selected = buf.readBoolean();
            if (blockId != null) {
                choices.add(new BlockChoice(blockId, name, targetCount, selected));
            }
        }
        int maxNodes = buf.readVarInt();
        return new SyncMassPlacementChoicesPayload(containerId, choices, maxNodes);
    }

    public static void write(FriendlyByteBuf buf, SyncMassPlacementChoicesPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.choices.size());
        for (BlockChoice choice : payload.choices) {
            buf.writeUtf(choice.blockId().toString(), 128);
            buf.writeUtf(limit(choice.name(), 256), 1024);
            buf.writeVarInt(choice.targetCount());
            buf.writeBoolean(choice.selected());
        }
        buf.writeVarInt(payload.maxNodes);
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
