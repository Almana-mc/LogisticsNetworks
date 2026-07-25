package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LnetNetworkFileTest {

    @Test
    void roundTripsCanonicalFormat() throws IOException {
        CompoundTag clipboard = new CompoundTag();
        clipboard.put("channels", new ListTag());
        LnetNetworkFile original = new LnetNetworkFile("Warehouse East",
                List.of(new LnetNetworkFile.NodeEntry("Input\\nOne", false, clipboard)));

        LnetNetworkFile restored = LnetNetworkFile.readString(original.writeString());
        assertEquals(original.networkName(), restored.networkName());
        assertEquals("Input\\nOne", restored.nodes().get(0).label());
        assertFalse(restored.nodes().get(0).visible());
    }

    @Test
    void readsLegacyHeader() throws IOException {
        LnetNetworkFile restored = LnetNetworkFile.readString(
                "lnet=1\nn=Legacy\nnode=Node A\nc={channels:[]}\nend=\n");
        assertEquals("Legacy", restored.networkName());
        assertEquals(1, restored.nodes().size());
    }
}
