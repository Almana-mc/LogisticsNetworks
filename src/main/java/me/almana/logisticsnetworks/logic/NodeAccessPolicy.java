package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class NodeAccessPolicy {

    private NodeAccessPolicy() {
    }

    public static boolean canAccess(UUID ownerUuid, UUID playerUuid) {
        if (ownerUuid == null || ownerUuid.equals(playerUuid)) {
            return true;
        }

        NodeAccessMode mode = Config.nodeAccessMode;
        if (mode == NodeAccessMode.ALL) {
            return true;
        }

        boolean teammates = FTBTeamsCompat.arePlayersInSameTeam(ownerUuid, playerUuid);
        boolean allies = mode == NodeAccessMode.ALLIES
                && FTBTeamsCompat.arePlayersAllied(ownerUuid, playerUuid);
        return mode.allows(teammates, allies);
    }

    public static boolean canAccess(UUID ownerUuid, UUID playerUuid, boolean adminMode) {
        return adminMode || canAccess(ownerUuid, playerUuid);
    }

    public static boolean canAccess(UUID ownerUuid, Player player) {
        return canAccess(ownerUuid, player.getUUID(), isAdminMode(player));
    }

    public static boolean canDelete(UUID ownerUuid, UUID playerUuid, boolean adminMode) {
        return adminMode || ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public static boolean canDelete(UUID ownerUuid, Player player) {
        return canDelete(ownerUuid, player.getUUID(), isAdminMode(player));
    }

    public static boolean isAdminMode(Player player) {
        return player.getData(Registration.ADMIN_MODE);
    }

    public static void setAdminMode(Player player, boolean enabled) {
        player.setData(Registration.ADMIN_MODE, enabled);
    }

}
