package me.almana.logisticsnetworks.data;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkMembershipTest {
    @Test
    void membershipChangesInvalidateExactlyOnce() {
        var registry = new NetworkRegistry();
        var network = registry.createNetwork();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        long generation = network.getGeneration();
        registry.addNodeToNetwork(network.getId(), first);
        assertEquals(generation + 1, network.getGeneration());
        registry.addNodeToNetwork(network.getId(), second);
        assertEquals(generation + 2, network.getGeneration());
        registry.removeNodeFromNetwork(network.getId(), second);
        assertEquals(generation + 3, network.getGeneration());
    }
}
