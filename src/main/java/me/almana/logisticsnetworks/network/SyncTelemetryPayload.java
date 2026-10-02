package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncTelemetryPayload(
        UUID networkId,
        boolean reset,
        List<Definition> added,
        List<ChannelSample> channels) implements CustomPacketPayload {

    public record Definition(int id, FlowResource resource) {
    }

    public record Entry(int id, long amount) {
    }

    public record ChannelSample(int typeOrdinal, long total, List<Entry> entries) {
    }

    public static final CustomPacketPayload.Type<SyncTelemetryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_telemetry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = StreamCodec
            .of(SyncTelemetryPayload::write, SyncTelemetryPayload::read);

    public static SyncTelemetryPayload read(RegistryFriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        boolean reset = buf.readBoolean();
        int addedCount = buf.readVarInt();
        List<Definition> added = new ArrayList<>(addedCount);
        for (int i = 0; i < addedCount; i++) {
            added.add(new Definition(buf.readVarInt(), readResource(buf)));
        }
        List<ChannelSample> channels = new ArrayList<>(LogisticsNodeEntity.CHANNEL_COUNT);
        for (int c = 0; c < LogisticsNodeEntity.CHANNEL_COUNT; c++) {
            int typeOrdinal = buf.readVarInt() - 1;
            long total = buf.readVarLong();
            int entryCount = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int e = 0; e < entryCount; e++) {
                entries.add(new Entry(buf.readVarInt(), buf.readVarLong()));
            }
            channels.add(new ChannelSample(typeOrdinal, total, entries));
        }
        return new SyncTelemetryPayload(networkId, reset, added, channels);
    }

    public static void write(RegistryFriendlyByteBuf buf, SyncTelemetryPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeBoolean(payload.reset);
        buf.writeVarInt(payload.added.size());
        for (Definition definition : payload.added) {
            buf.writeVarInt(definition.id());
            writeResource(buf, definition.resource());
        }
        for (ChannelSample channel : payload.channels) {
            buf.writeVarInt(channel.typeOrdinal() + 1);
            buf.writeVarLong(channel.total());
            buf.writeVarInt(channel.entries().size());
            for (Entry entry : channel.entries()) {
                buf.writeVarInt(entry.id());
                buf.writeVarLong(entry.amount());
            }
        }
    }

    private static FlowResource readResource(RegistryFriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> new FlowResource.Item(ItemStack.STREAM_CODEC.decode(buf));
            case 1 -> new FlowResource.Fluid(FluidStack.STREAM_CODEC.decode(buf));
            default -> new FlowResource.Chemical(buf.readUtf());
        };
    }

    private static void writeResource(RegistryFriendlyByteBuf buf, FlowResource resource) {
        switch (resource) {
            case FlowResource.Item item -> {
                buf.writeByte(0);
                ItemStack.STREAM_CODEC.encode(buf, item.stack());
            }
            case FlowResource.Fluid fluid -> {
                buf.writeByte(1);
                FluidStack.STREAM_CODEC.encode(buf, fluid.stack());
            }
            case FlowResource.Chemical chemical -> {
                buf.writeByte(2);
                buf.writeUtf(chemical.id());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
