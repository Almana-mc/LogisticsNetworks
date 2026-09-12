package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record SyncMassPlacementRequirementsPayload(
        int containerId,
        boolean pending,
        List<Requirement> requirements) implements CustomPacketPayload {

    public record Requirement(ItemStack item, int required, int inventory, long storageStored,
                              boolean storageCraftable, boolean storageUsed, boolean missing) {
    }

    public static final Type<SyncMassPlacementRequirementsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "sync_mass_placement_requirements"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMassPlacementRequirementsPayload> STREAM_CODEC =
            StreamCodec.of(SyncMassPlacementRequirementsPayload::write,
                    SyncMassPlacementRequirementsPayload::read);

    private static SyncMassPlacementRequirementsPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean pending = buf.readBoolean();
        int size = buf.readVarInt();
        List<Requirement> requirements = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            requirements.add(new Requirement(
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()));
        }
        return new SyncMassPlacementRequirementsPayload(containerId, pending, requirements);
    }

    private static void write(RegistryFriendlyByteBuf buf, SyncMassPlacementRequirementsPayload payload) {
        buf.writeVarInt(payload.containerId());
        buf.writeBoolean(payload.pending());
        buf.writeVarInt(payload.requirements().size());
        for (Requirement requirement : payload.requirements()) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, requirement.item());
            buf.writeVarInt(requirement.required());
            buf.writeVarInt(requirement.inventory());
            buf.writeVarLong(requirement.storageStored());
            buf.writeBoolean(requirement.storageCraftable());
            buf.writeBoolean(requirement.storageUsed());
            buf.writeBoolean(requirement.missing());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
