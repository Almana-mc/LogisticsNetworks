package me.almana.logisticsnetworks.event;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID)
public class EventHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LogisticsNodeEntity node) || node.level().isClientSide())
            return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        UUID networkId = node.getNetworkId();
        if (networkId != null) {
            NetworkRegistry.get(serverLevel).markNetworkDirty(networkId);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());

        if (!(stack.getItem() instanceof WrenchItem))
            return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.5));
        for (LogisticsNodeEntity node : nodes) {
            if (node.getAttachedPos().equals(pos) && node.isActive()) {
                event.setUseBlock(TriState.FALSE);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onNeighborUpdate(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level))
            return;

        AABB searchBox = new AABB(event.getPos()).inflate(1.0);
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class, searchBox);

        for (LogisticsNodeEntity node : nodes) {
            if (node.isActive() && node.getNetworkId() != null && node.getAttachedPos().equals(event.getPos())) {
                NetworkRegistry.get(level).markNetworkDirty(node.getNetworkId());
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        BlockPos pos = event.getPos();
        List<LogisticsNodeEntity> nodes = serverLevel.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.1));

        for (LogisticsNodeEntity node : nodes) {
            if (node.getAttachedPos().equals(pos)) {
                if (node.getNetworkId() != null) {
                    NetworkRegistry.get(serverLevel).removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
                }

                if (Config.dropNodeItem) {
                    node.spawnAtLocation(Registration.LOGISTICS_NODE_ITEM.get());
                }
                node.dropFilters();
                node.dropUpgrades();
                node.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity().level() instanceof ServerLevel level))
            return;

        if (event.getContainer() instanceof NodeMenu menu && event.getEntity() instanceof ServerPlayer player) {
            LogisticsNodeEntity node = menu.getNode();
            if (node != null && node.isActive() && node.getNetworkId() != null) {
                LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(node.getNetworkId());
                if (network != null
                        && NodeUpgradeData.needsDimensionalUpgradeWarning(node, network, level.getServer())) {
                    player.sendSystemMessage(
                            Component.translatable("gui.logisticsnetworks.dimensional_upgrade_warning"));
                }
            }
        }

        BlockPos containerPos = null;
        for (Slot slot : event.getContainer().slots) {
            if (slot.container instanceof BlockEntity be) {
                containerPos = be.getBlockPos();
                break;
            }
        }

        if (containerPos != null) {
            List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                    new AABB(containerPos).inflate(0.1));
            for (LogisticsNodeEntity node : nodes) {
                if (node.isActive() && node.getNetworkId() != null && node.getAttachedPos().equals(containerPos)) {
                    NetworkRegistry.get(level).markNetworkDirty(node.getNetworkId());
                }
            }
        }
    }
}
