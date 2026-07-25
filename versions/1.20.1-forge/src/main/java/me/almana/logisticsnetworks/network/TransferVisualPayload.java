package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TransferVisualPayload(int color, List<Path> paths) implements CustomPacketPayload {

    public enum Shape {
        FULL,
        OUTBOUND,
        INBOUND
    }

    public record Path(UUID sourceId, UUID targetId, int sourceEntityId, int targetEntityId,
            BlockPos sourcePos, BlockPos targetPos, int typeOrdinal, Shape shape) {
    }

    public static final CustomPacketPayload.Type<TransferVisualPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "transfer_visual"));

    public static final StreamCodec<FriendlyByteBuf, TransferVisualPayload> STREAM_CODEC = StreamCodec
            .of(TransferVisualPayload::write, TransferVisualPayload::read);

    public static TransferVisualPayload read(FriendlyByteBuf buf) {
        int color = buf.readInt();
        int count = buf.readVarInt();
        List<Path> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(new Path(buf.readUUID(), buf.readUUID(), buf.readInt(), buf.readInt(),
                    buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), Shape.values()[buf.readByte()]));
        }
        return new TransferVisualPayload(color, paths);
    }

    public static void write(FriendlyByteBuf buf, TransferVisualPayload payload) {
        buf.writeInt(payload.color);
        buf.writeVarInt(payload.paths.size());
        for (Path path : payload.paths) {
            buf.writeUUID(path.sourceId);
            buf.writeUUID(path.targetId);
            buf.writeInt(path.sourceEntityId);
            buf.writeInt(path.targetEntityId);
            buf.writeBlockPos(path.sourcePos);
            buf.writeBlockPos(path.targetPos);
            buf.writeVarInt(path.typeOrdinal);
            buf.writeByte(path.shape.ordinal());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
