package me.almana.logisticsnetworks.upgrade;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class NodeUpgradeData {

    private NodeUpgradeData() {
    }

    public static int getItemOperationCap(LogisticsNodeEntity node) {
        return getItemOperationCap(getUpgradeTier(node));
    }

    public static int getItemOperationCap(int tier) {
        return UpgradeLimitsConfig.get(tier).itemBatch();
    }

    public static int getEnergyOperationCap(LogisticsNodeEntity node) {
        return getEnergyOperationCap(getUpgradeTier(node));
    }

    public static int getEnergyOperationCap(int tier) {
        return UpgradeLimitsConfig.get(tier).energyBatch();
    }

    public static int getFluidOperationCapMb(LogisticsNodeEntity node) {
        return getFluidOperationCapMb(getUpgradeTier(node));
    }

    public static int getFluidOperationCapMb(int tier) {
        return UpgradeLimitsConfig.get(tier).fluidBatch();
    }

    public static int getChemicalOperationCap(LogisticsNodeEntity node) {
        return getChemicalOperationCap(getUpgradeTier(node));
    }

    public static int getChemicalOperationCap(int tier) {
        return UpgradeLimitsConfig.get(tier).chemicalBatch();
    }

    public static int getSourceOperationCap(LogisticsNodeEntity node) {
        return getSourceOperationCap(getUpgradeTier(node));
    }

    public static int getSourceOperationCap(int tier) {
        return UpgradeLimitsConfig.get(tier).sourceBatch();
    }

    public static int getMinTickDelay(LogisticsNodeEntity node) {
        return getMinTickDelay(getUpgradeTier(node));
    }

    public static int getMinTickDelay(int tier) {
        return UpgradeLimitsConfig.get(tier).minTicks();
    }

    public static boolean hasDimensionalUpgrade(LogisticsNodeEntity node) {
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            if (node.getUpgradeItem(i).is(Registration.DIMENSIONAL_UPGRADE.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMekanismChemicalUpgrade(LogisticsNodeEntity node) {
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            if (node.getUpgradeItem(i).is(Registration.MEKANISM_CHEMICAL_UPGRADE.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasArsSourceUpgrade(LogisticsNodeEntity node) {
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            if (node.getUpgradeItem(i).is(Registration.ARS_SOURCE_UPGRADE.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean needsDimensionalUpgradeWarning(LogisticsNodeEntity node, LogisticsNetwork network,
            MinecraftServer server) {
        if (network == null || server == null || hasDimensionalUpgrade(node))
            return false;

        ResourceKey<Level> nodeDimension = node.level().dimension();

        // Determine which channel indices and modes this node uses
        boolean[] hasExport = new boolean[LogisticsNodeEntity.CHANNEL_COUNT];
        boolean[] hasImport = new boolean[LogisticsNodeEntity.CHANNEL_COUNT];
        boolean hasAnyChannel = false;

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData ch = node.getChannel(i);
            if (ch == null || !ch.isEnabled())
                continue;
            if (ch.getMode() == ChannelMode.EXPORT) {
                hasExport[i] = true;
                hasAnyChannel = true;
            } else if (ch.getMode() == ChannelMode.IMPORT) {
                hasImport[i] = true;
                hasAnyChannel = true;
            }
        }

        if (!hasAnyChannel)
            return false;

        for (UUID otherId : network.getNodeUuids()) {
            if (otherId.equals(node.getUUID()))
                continue;

            Entity entity = findEntity(server, otherId);
            if (!(entity instanceof LogisticsNodeEntity otherNode) || !otherNode.isValidNode())
                continue;
            if (otherNode.level().dimension().equals(nodeDimension))
                continue;

            // Other node is in a different dimension — check if it has a complementary channel
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData otherCh = otherNode.getChannel(i);
                if (otherCh == null || !otherCh.isEnabled())
                    continue;

                // This node exports on channel i → other node imports on channel i (same type)
                if (hasExport[i] && otherCh.getMode() == ChannelMode.IMPORT
                        && node.getChannel(i).getType() == otherCh.getType()) {
                    return true;
                }
                // This node imports on channel i → other node exports on channel i (same type)
                if (hasImport[i] && otherCh.getMode() == ChannelMode.EXPORT
                        && node.getChannel(i).getType() == otherCh.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null)
                return entity;
        }
        return null;
    }

    public static int getUpgradeTier(LogisticsNodeEntity node) {
        int maxTier = 0;
        for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
            maxTier = Math.max(maxTier, getTier(node.getUpgradeItem(i)));
            if (maxTier == 4)
                break;
        }
        return maxTier;
    }

    private static int getTier(ItemStack stack) {
        if (stack.isEmpty())
            return 0;
        if (stack.is(Registration.NETHERITE_UPGRADE.get()))
            return 4;
        if (stack.is(Registration.DIAMOND_UPGRADE.get()))
            return 3;
        if (stack.is(Registration.GOLD_UPGRADE.get()))
            return 2;
        if (stack.is(Registration.IRON_UPGRADE.get()))
            return 1;
        return 0;
    }
}
