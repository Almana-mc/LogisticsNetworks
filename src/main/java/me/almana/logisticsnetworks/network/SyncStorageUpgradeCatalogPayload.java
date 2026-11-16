package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SyncStorageUpgradeCatalogPayload(int containerId, int entityId, int preferredSlot,
                                               @Nullable StorageBackend backend, boolean available,
                                               List<Entry> entries) implements CustomPacketPayload {

    public record Entry(ItemStack item, long stored, boolean craftable) {
    }

    public static final Type<SyncStorageUpgradeCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_storage_upgrade_catalog"));
    public static final StreamCodec<FriendlyByteBuf, SyncStorageUpgradeCatalogPayload> STREAM_CODEC =
            StreamCodec.of(SyncStorageUpgradeCatalogPayload::write, SyncStorageUpgradeCatalogPayload::read);

    private static SyncStorageUpgradeCatalogPayload read(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int entityId = buf.readVarInt();
        int preferredSlot = buf.readVarInt();
        StorageBackend backend = buf.readBoolean() ? StorageBackend.values()[buf.readVarInt()] : null;
        boolean available = buf.readBoolean();
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(buf.readItem(), buf.readVarLong(), buf.readBoolean()));
        }
        return new SyncStorageUpgradeCatalogPayload(
                containerId, entityId, preferredSlot, backend, available, entries);
    }

    private static void write(FriendlyByteBuf buf, SyncStorageUpgradeCatalogPayload payload) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.entityId());
        buf.writeVarInt(payload.preferredSlot());
        buf.writeBoolean(payload.backend() != null);
        if (payload.backend() != null) buf.writeVarInt(payload.backend().ordinal());
        buf.writeBoolean(payload.available());
        buf.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries()) {
            writeEntry(buf, entry);
        }
    }

    static void writeEntry(FriendlyByteBuf buf, Entry entry) {
        buf.writeItem(entry.item());
        buf.writeVarLong(entry.stored());
        buf.writeBoolean(entry.craftable());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
