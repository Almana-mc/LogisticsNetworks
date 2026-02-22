package me.almana.logisticsnetworks.upgrade;

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

        for (UUID otherId : network.getNodeUuids()) {
            if (otherId.equals(node.getUUID()))
                continue;

            Entity entity = findEntity(server, otherId);
            if (entity instanceof LogisticsNodeEntity otherNode && otherNode.isValidNode()) {
                if (!otherNode.level().dimension().equals(nodeDimension)) {
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
