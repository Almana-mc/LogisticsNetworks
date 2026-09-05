package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;

import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class NodeAccessPolicy {

    private NodeAccessPolicy() {
    }

    public static boolean canAccess(UUID ownerUuid, UUID playerUuid) {
        return canAccess(ownerUuid, playerUuid,
                () -> FTBTeamsCompat.arePlayersInSameTeam(ownerUuid, playerUuid));
    }

    public static boolean canAccess(UUID ownerUuid, UUID playerUuid, Set<UUID> teammateIds) {
        return canAccess(ownerUuid, playerUuid, () -> teammateIds.contains(ownerUuid));
    }

    private static boolean canAccess(UUID ownerUuid, UUID playerUuid, BooleanSupplier teammates) {
        if (ownerUuid == null || ownerUuid.equals(playerUuid)) {
            return true;
        }

        NodeAccessMode mode = Config.nodeAccessMode;
        if (mode == NodeAccessMode.ALL) {
            return true;
        }

        boolean sameTeam = teammates.getAsBoolean();
        boolean allies = mode == NodeAccessMode.ALLIES
                && FTBTeamsCompat.arePlayersAllied(ownerUuid, playerUuid);
        return mode.allows(sameTeam, allies);
    }
}
