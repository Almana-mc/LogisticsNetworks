package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAccessPolicyTest {
    private final NodeAccessMode originalMode = Config.nodeAccessMode;

    @AfterEach
    void restoreMode() {
        Config.nodeAccessMode = originalMode;
    }

    @Test
    void modesApplyStrangerTeammateAndAllyRelations() {
        assertFalse(NodeAccessMode.TEAMS.allows(false, false));
        assertTrue(NodeAccessMode.TEAMS.allows(true, false));
        assertFalse(NodeAccessMode.TEAMS.allows(false, true));

        assertTrue(NodeAccessMode.ALL.allows(false, false));
        assertTrue(NodeAccessMode.ALL.allows(true, false));
        assertTrue(NodeAccessMode.ALL.allows(false, true));

        assertFalse(NodeAccessMode.ALLIES.allows(false, false));
        assertTrue(NodeAccessMode.ALLIES.allows(true, false));
        assertTrue(NodeAccessMode.ALLIES.allows(false, true));
    }

    @Test
    void serializedNamesUseConfiguredValues() {
        assertEquals("Teams", NodeAccessMode.TEAMS.serializedName());
        assertEquals("All", NodeAccessMode.ALL.serializedName());
        assertEquals("Allies", NodeAccessMode.ALLIES.serializedName());
        assertEquals(NodeAccessMode.TEAMS, NodeAccessMode.fromSerializedName("Teams"));
        assertEquals(NodeAccessMode.ALL, NodeAccessMode.fromSerializedName("All"));
        assertEquals(NodeAccessMode.ALLIES, NodeAccessMode.fromSerializedName("Allies"));
        assertThrows(IllegalArgumentException.class, () -> NodeAccessMode.fromSerializedName("team"));
    }

    @Test
    void ownersAndUnownedNetworksRemainAccessibleInEveryMode() {
        UUID owner = UUID.randomUUID();
        for (NodeAccessMode mode : NodeAccessMode.values()) {
            Config.nodeAccessMode = mode;
            assertTrue(NodeAccessPolicy.canAccess(owner, owner));
            assertTrue(NodeAccessPolicy.canAccess(null, owner));
        }
    }

    @Test
    void missingFtbTeamsDeniesRelationsExceptInAllMode() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        assertFalse(FTBTeamsCompat.isLoaded());

        Config.nodeAccessMode = NodeAccessMode.TEAMS;
        assertFalse(NodeAccessPolicy.canAccess(owner, stranger));
        Config.nodeAccessMode = NodeAccessMode.ALLIES;
        assertFalse(NodeAccessPolicy.canAccess(owner, stranger));
        Config.nodeAccessMode = NodeAccessMode.ALL;
        assertTrue(NodeAccessPolicy.canAccess(owner, stranger));
    }

    @Test
    void networkDirectoryPreservesRegistryOrderWhileApplyingPolicy() {
        UUID player = UUID.randomUUID();
        NetworkRegistry registry = new NetworkRegistry();
        registry.createNetwork("Stranger", UUID.randomUUID());
        registry.createNetwork("Mine", player);
        registry.createNetwork("Unowned", null);
        var registryOrder = registry.getAllNetworks().values().stream().toList();

        Config.nodeAccessMode = NodeAccessMode.ALL;
        assertEquals(registryOrder, registry.getNetworksForPlayer(player));

        Config.nodeAccessMode = NodeAccessMode.TEAMS;
        var expected = registryOrder.stream()
                .filter(network -> network.getOwnerUuid() == null || network.getOwnerUuid().equals(player))
                .map(LogisticsNetwork::getId)
                .toList();
        assertEquals(expected, registry.getNetworksForPlayer(player).stream().map(LogisticsNetwork::getId).toList());
    }

    @Test
    void precomputedTeammatesUseTheSamePolicy() {
        UUID owner = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        Config.nodeAccessMode = NodeAccessMode.TEAMS;
        assertTrue(NodeAccessPolicy.canAccess(owner, player, Set.of(owner)));
        assertFalse(NodeAccessPolicy.canAccess(owner, player, Set.of()));
        Config.nodeAccessMode = NodeAccessMode.ALLIES;
        assertTrue(NodeAccessPolicy.canAccess(owner, player, Set.of(owner)));
        assertFalse(NodeAccessPolicy.canAccess(owner, player, Set.of()));
        Config.nodeAccessMode = NodeAccessMode.ALL;
        assertTrue(NodeAccessPolicy.canAccess(owner, player, Set.of()));
    }

    @Test
    void directNodeAccessRetainsItsOperatorException() {
        UUID owner = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class, CALLS_REAL_METHODS);
        doReturn(owner).when(node).getOwnerUUID();
        Player stranger = mock(Player.class);
        when(stranger.getUUID()).thenReturn(strangerId);
        ServerPlayer operator = mock(ServerPlayer.class);
        when(operator.getUUID()).thenReturn(strangerId);
        when(operator.permissions()).thenReturn(PermissionSet.ALL_PERMISSIONS);

        for (NodeAccessMode mode : NodeAccessMode.values()) {
            Config.nodeAccessMode = mode;
            assertEquals(mode == NodeAccessMode.ALL, node.isOwnedBy(stranger));
            assertTrue(node.isOwnedBy(operator));
        }
    }
}
