package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record GraphMenuContext(BlockPos computerPos, ResourceLocation computerDimension, UUID networkId,
                               Origin origin) {
    public enum Origin {
        GRAPH,
        TABLE
    }

    public GraphMenuContext(BlockPos computerPos, ResourceLocation computerDimension, UUID networkId) {
        this(computerPos, computerDimension, networkId, Origin.GRAPH);
    }

    public static GraphMenuContext read(FriendlyByteBuf buf) {
        BlockPos computerPos = buf.readBlockPos();
        ResourceLocation dimension = buf.readResourceLocation();
        UUID networkId = buf.readUUID();
        int origin = buf.readVarInt();
        return new GraphMenuContext(computerPos, dimension, networkId,
                origin == Origin.TABLE.ordinal() ? Origin.TABLE : Origin.GRAPH);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(computerPos);
        buf.writeResourceLocation(computerDimension);
        buf.writeUUID(networkId);
        buf.writeVarInt(origin.ordinal());
    }

    public boolean stillValid(Player player) {
        if (!player.level().dimension().location().equals(computerDimension)
                || player.distanceToSqr(computerPos.getCenter()) >= 64.0) return false;
        if (player.level().isClientSide) return true;
        if (!(player.level().getBlockEntity(computerPos) instanceof ComputerBlockEntity)) return false;
        LogisticsNetwork network = NetworkRegistry.get((ServerLevel) player.level()).getNetwork(networkId);
        return network != null && (NodeAccessPolicy.canAccess(network.getOwnerUuid(), player.getUUID())
                || player.hasPermissions(2));
    }

    public boolean canEdit(Player player, LogisticsNodeEntity node) {
        if (node == null || !stillValid(player)) return false;
        if (player.level().isClientSide) return true;
        return node.isAlive() && node.isValidNode() && networkId.equals(node.getNetworkId())
                && node.level() instanceof ServerLevel level && level.getEntity(node.getUUID()) == node
                && NetworkRegistry.get(level).getNetwork(networkId).getNodeUuids().contains(node.getUUID())
                && node.isOwnedBy(player);
    }
}
