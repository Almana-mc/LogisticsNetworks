package me.almana.logisticsnetworks.integration.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FTBTeamsCompat {

    private static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private static Boolean loaded = null;

    private FTBTeamsCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(FTB_TEAMS_MOD_ID);
        }
        return loaded;
    }

    public static boolean arePlayersInSameTeam(UUID player1, UUID player2) {
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
    }

    public static Set<UUID> getTeammateIds(UUID playerUuid) {
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
    }
}
