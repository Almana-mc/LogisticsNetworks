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

    // Future will be part of configuration, loaded once per startup
    private static final int NO_UPGRADE_ITEM_CAP = 8;
    private static final int IRON_ITEM_CAP = 16;
    private static final int GOLD_ITEM_CAP = 32;
    private static final int DIAMOND_ITEM_CAP = 64;
    private static final int NETHERITE_ITEM_CAP = 10_000;

    private static final int NO_UPGRADE_ENERGY_CAP = 2_000;
    private static final int IRON_ENERGY_CAP = 10_000;
    private static final int GOLD_ENERGY_CAP = 50_000;
    private static final int DIAMOND_ENERGY_CAP = 250_000;
    private static final int NETHERITE_ENERGY_CAP = Integer.MAX_VALUE;

    private static final int NO_UPGRADE_FLUID_CAP_MB = 500;
    private static final int IRON_FLUID_CAP_MB = 1_000;
    private static final int GOLD_FLUID_CAP_MB = 5_000;
    private static final int DIAMOND_FLUID_CAP_MB = 20_000;
    private static final int NETHERITE_FLUID_CAP_MB = 1_000_000;

    private static final int NO_UPGRADE_MIN_DELAY = 20;
    private static final int IRON_MIN_DELAY = 10;
    private static final int GOLD_MIN_DELAY = 5;
    private static final int DIAMOND_MIN_DELAY = 1;

    private NodeUpgradeData() {
    }

    public static int getItemOperationCap(LogisticsNodeEntity node) {
        return getItemOperationCap(getUpgradeTier(node));
    }

    public static int getItemOperationCap(int tier) {
        return switch (tier) {
            case 1 -> IRON_ITEM_CAP;
            case 2 -> GOLD_ITEM_CAP;
            case 3 -> DIAMOND_ITEM_CAP;
            case 4 -> NETHERITE_ITEM_CAP;
            default -> NO_UPGRADE_ITEM_CAP;
        };
    }

    public static int getEnergyOperationCap(LogisticsNodeEntity node) {
        return getEnergyOperationCap(getUpgradeTier(node));
    }

    public static int getEnergyOperationCap(int tier) {
        return switch (tier) {
            case 1 -> IRON_ENERGY_CAP;
            case 2 -> GOLD_ENERGY_CAP;
            case 3 -> DIAMOND_ENERGY_CAP;
            case 4 -> NETHERITE_ENERGY_CAP;
            default -> NO_UPGRADE_ENERGY_CAP;
        };
    }

    public static int getFluidOperationCapMb(LogisticsNodeEntity node) {
        return getFluidOperationCapMb(getUpgradeTier(node));
    }

    public static int getFluidOperationCapMb(int tier) {
        return switch (tier) {
            case 1 -> IRON_FLUID_CAP_MB;
            case 2 -> GOLD_FLUID_CAP_MB;
            case 3 -> DIAMOND_FLUID_CAP_MB;
            case 4 -> NETHERITE_FLUID_CAP_MB;
            default -> NO_UPGRADE_FLUID_CAP_MB;
        };
    }

    public static int getChemicalOperationCap(LogisticsNodeEntity node) {
        return getFluidOperationCapMb(getUpgradeTier(node));
    }

    public static int getChemicalOperationCap(int tier) {
        return getFluidOperationCapMb(tier);
    }

    public static int getMinTickDelay(LogisticsNodeEntity node) {
        return getMinTickDelay(getUpgradeTier(node));
    }

    public static int getMinTickDelay(int tier) {
        return switch (tier) {
            case 1 -> IRON_MIN_DELAY;
            case 2 -> GOLD_MIN_DELAY;
            case 3 -> DIAMOND_MIN_DELAY;
            case 4 -> DIAMOND_MIN_DELAY;
            default -> NO_UPGRADE_MIN_DELAY;
        };
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
