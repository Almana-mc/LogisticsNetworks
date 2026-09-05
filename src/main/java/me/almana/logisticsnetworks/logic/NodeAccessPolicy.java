package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;

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
}
