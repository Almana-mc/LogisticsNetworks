package me.almana.logisticsnetworks.integration.ftbteams;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class FTBTeamsCompat {

    private FTBTeamsCompat() {
    }

    public static boolean isLoaded() {
        // 26.1 FTB API pending
        /*
        if (loaded == null) {
            loaded = ModList.get().isLoaded(FTB_TEAMS_MOD_ID);
        }
        return loaded;
        */
        return false;
    }

    public static boolean arePlayersInSameTeam(UUID player1, UUID player2) {
        // 26.1 FTB API pending
        /*
        if (!isLoaded())
            return false;
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (!api.isManagerLoaded())
                return false;
            return api.getManager().arePlayersInSameTeam(player1, player2);
        } catch (Exception e) {
            return false;
        }
        */
        return false;
    }

    public static Set<UUID> getTeammateIds(UUID playerUuid) {
        // 26.1 FTB API pending
        /*
        if (!isLoaded())
            return Collections.emptySet();
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (!api.isManagerLoaded())
                return Collections.emptySet();
            return api.getManager().getTeamForPlayerID(playerUuid)
                    .map(Team::getMembers)
                    .map(members -> {
                        Set<UUID> teammates = new HashSet<>(members);
                        teammates.remove(playerUuid);
                        return teammates;
                    })
                    .orElse(Collections.emptySet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
        */
        return Collections.emptySet();
    }
}
