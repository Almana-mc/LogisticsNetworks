package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public static void write(RegistryFriendlyByteBuf buf, SetNodeUpgradeItemPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.upgradeSlot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.upgradeItem);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
