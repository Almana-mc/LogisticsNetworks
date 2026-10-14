package me.almana.logisticsnetworks.menu;

import io.netty.buffer.Unpooled;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SyncChannelDataPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.function.Consumer;

public final class NodeMenuSync {

    // Forge caps open buffers
    private static final int OPEN_BUFFER_BUDGET = 30_000;

    private NodeMenuSync() {
    }

    public static boolean fitsOpenBuffer(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf probe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(probe);
            return probe.readableBytes() <= OPEN_BUFFER_BUDGET;
        } finally {
            probe.release();
        }
    }

    public static void openScreen(ServerPlayer player, MenuProvider provider, LogisticsNodeEntity node,
            int selectedChannel) {
        boolean inline = fitsOpenBuffer(buf -> write(buf, node, selectedChannel, true));
        NetworkHooks.openScreen(player, provider, buf -> write(buf, node, selectedChannel, inline));
        if (inline)
            return;
        // Too big: channels follow separately
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            NetworkHandler.sendToPlayer(player, new SyncChannelDataPayload(node.getId(), i,
                    node.getChannel(i).save(node.level().registryAccess())));
        }
    }

    private static void write(FriendlyByteBuf buf, LogisticsNodeEntity node, int selectedChannel,
            boolean withChannels) {
        buf.writeVarInt(node.getId());
        buf.writeUUID(node.getUUID());
        buf.writeResourceLocation(node.level().dimension().location());
        buf.writeVarInt(selectedChannel);

        UUID networkId = node.getNetworkId();
        buf.writeBoolean(networkId != null);
        if (networkId != null) {
            buf.writeUUID(networkId);
        }
        buf.writeUtf(node.getNetworkName());
        buf.writeUtf(node.getNodeLabel());
        buf.writeBoolean(node.isRenderVisible());

        buf.writeBoolean(withChannels);
        for (int i = 0; withChannels && i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            buf.writeNbt(node.getChannel(i).save(node.level().registryAccess()));
        }
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            buf.writeItem(node.getUpgradeItem(i));
        }
    }

    public static ClientNodeState read(FriendlyByteBuf buf, Player player) {
        int entityId = buf.readVarInt();
        UUID nodeId = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        int selectedChannel = Math.max(0, Math.min(LogisticsNodeEntity.CHANNEL_COUNT - 1, buf.readVarInt()));
        LogisticsNodeEntity node = findOrCreateClientNode(player, entityId, nodeId, dimension);

        node.setNetworkId(buf.readBoolean() ? buf.readUUID() : null);
        node.setNetworkName(buf.readUtf());
        node.setNodeLabel(buf.readUtf());
        node.setRenderVisible(buf.readBoolean());

        boolean withChannels = buf.readBoolean();
        for (int i = 0; withChannels && i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            node.getChannel(i).load(buf.readNbt(), player.level().registryAccess());
        }
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            node.setUpgradeItem(i, buf.readItem());
        }
        return new ClientNodeState(entityId, selectedChannel, node);
    }

    public static LogisticsNodeEntity findOrCreateClientNode(Player player, int entityId, UUID nodeId,
            ResourceLocation dimension) {
        Entity entity = player.level().getEntity(entityId);
        if (player.level().dimension().location().equals(dimension)
                && entity instanceof LogisticsNodeEntity node && node.getUUID().equals(nodeId)) {
            return node;
        }
        LogisticsNodeEntity node = new LogisticsNodeEntity(Registration.LOGISTICS_NODE.get(), player.level());
        node.setId(entityId);
        node.setUUID(nodeId);
        return node;
    }

    public record ClientNodeState(int entityId, int selectedChannel, LogisticsNodeEntity node) {
    }
}
