package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetNodeUpgradeItemPayload(
        int entityId,
        int upgradeSlot,
        ItemStack upgradeItem) implements CustomPacketPayload {

    public static final Type<SetNodeUpgradeItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_node_upgrade_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetNodeUpgradeItemPayload> STREAM_CODEC = StreamCodec
            .of(SetNodeUpgradeItemPayload::write, SetNodeUpgradeItemPayload::read);

    public static SetNodeUpgradeItemPayload read(RegistryFriendlyByteBuf buf) {
        return new SetNodeUpgradeItemPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readItem());
    }

    public static void write(RegistryFriendlyByteBuf buf, SetNodeUpgradeItemPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.upgradeSlot);
        buf.writeItem(payload.upgradeItem);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

