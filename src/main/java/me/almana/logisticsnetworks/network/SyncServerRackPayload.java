package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SyncServerRackPayload(BlockPos rackPos, ServerRackConfig config, Side left, Side right)
        implements CustomPacketPayload {

    private static final int CHANNELS = LogisticsNodeEntity.CHANNEL_COUNT;

    public static final Type<SyncServerRackPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_server_rack"));

    public static final StreamCodec<FriendlyByteBuf, SyncServerRackPayload> STREAM_CODEC = StreamCodec
            .of(SyncServerRackPayload::write, SyncServerRackPayload::read);

    public record Side(boolean exists, String name, int color, List<String> channelNames, int[] exports,
            int[] imports) {

        public static final Side EMPTY = new Side(false, "", 0, Collections.nCopies(CHANNELS, ""),
                new int[CHANNELS], new int[CHANNELS]);

        @Nullable
        public ChannelType type(int channel) {
            int mask = exports[channel] | imports[channel];
            return mask == 0 ? null : ChannelType.values()[Integer.numberOfTrailingZeros(mask)];
        }

        private static Side read(FriendlyByteBuf buf) {
            boolean exists = buf.readBoolean();
            String name = buf.readUtf(64);
            int color = buf.readInt();
            List<String> names = new ArrayList<>(CHANNELS);
            int[] exports = new int[CHANNELS];
            int[] imports = new int[CHANNELS];
            for (int i = 0; i < CHANNELS; i++) {
                names.add(buf.readUtf(24));
                exports[i] = buf.readVarInt();
                imports[i] = buf.readVarInt();
            }
            return new Side(exists, name, color, names, exports, imports);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(exists);
            buf.writeUtf(name, 64);
            buf.writeInt(color);
            for (int i = 0; i < CHANNELS; i++) {
                buf.writeUtf(channelNames.get(i), 24);
                buf.writeVarInt(exports[i]);
                buf.writeVarInt(imports[i]);
            }
        }
    }

    public static SyncServerRackPayload read(FriendlyByteBuf buf) {
        return new SyncServerRackPayload(buf.readBlockPos(), ServerRackConfig.STREAM_CODEC.decode(buf),
                Side.read(buf), Side.read(buf));
    }

    public static void write(FriendlyByteBuf buf, SyncServerRackPayload payload) {
        buf.writeBlockPos(payload.rackPos);
        ServerRackConfig.STREAM_CODEC.encode(buf, payload.config);
        payload.left.write(buf);
        payload.right.write(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
