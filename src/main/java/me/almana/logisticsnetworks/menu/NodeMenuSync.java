package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class NodeMenuSync {

    private NodeMenuSync() {
    }

    public static void write(FriendlyByteBuf buf, LogisticsNodeEntity node, HolderLookup.Provider provider,
            int selectedChannel) {
        buf.writeVarInt(node.getId());
        buf.writeVarInt(selectedChannel);

        UUID networkId = node.getNetworkId();
        buf.writeBoolean(networkId != null);
        if (networkId != null) {
            buf.writeUUID(networkId);
        }
        buf.writeUtf(node.getNetworkName());
        buf.writeUtf(node.getNodeLabel());
        buf.writeBoolean(node.isRenderVisible());

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            buf.writeNbt(node.getChannel(i).save(provider));
        }
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            CompoundTag entry = new CompoundTag();
            entry.store("Item", ItemStack.OPTIONAL_CODEC, node.getUpgradeItem(i));
            buf.writeNbt(entry);
        }
    }

    public static ClientNodeState read(FriendlyByteBuf buf, Player player) {
        int entityId = buf.readVarInt();
        int selectedChannel = Math.max(0, Math.min(LogisticsNodeEntity.CHANNEL_COUNT - 1, buf.readVarInt()));
        LogisticsNodeEntity node = findOrCreateClientNode(player, entityId);

        UUID networkId = buf.readBoolean() ? buf.readUUID() : null;
        node.setNetworkId(networkId);
        node.setNetworkName(buf.readUtf());
        node.setNodeLabel(buf.readUtf());
        node.setRenderVisible(buf.readBoolean());

        HolderLookup.Provider provider = player.level().registryAccess();
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                node.getChannel(i).load(tag, provider);
            }
        }
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                node.setUpgradeItem(i, tag.read("Item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
            }
        }

        return new ClientNodeState(entityId, selectedChannel, node);
    }

    public static LogisticsNodeEntity findOrCreateClientNode(Player player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof LogisticsNodeEntity node) {
            return node;
        }

        LogisticsNodeEntity node = new LogisticsNodeEntity(Registration.LOGISTICS_NODE.get(), player.level());
        node.setId(entityId);
        return node;
    }

    public record ClientNodeState(int entityId, int selectedChannel, LogisticsNodeEntity node) {
    }
}
