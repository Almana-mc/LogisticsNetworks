package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommitBulkTest {
    @BeforeAll
    static void bootstrap() { SophisticatedTransferTest.bootstrap(); }
    @BeforeEach
    void mark() { ThreadGuard.markServerThread(); }
    @AfterEach
    void clear() { ThreadGuard.clearServerThread(); }

    @Test
    void commitKeepsActualWholeCoreInsertionAndRollsBackRejectedExtraction() {
        for (boolean reject : new boolean[]{false, true}) {
            var target = new SophisticatedInventoryFixture(1, 64);
            target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));
            target.saves = 0;
            target.changes = 0;
            var source = spy(TransferParityTest.inventory(8));
            if (reject) doAnswer(call -> target.inventory.getAmountAsInt(0) > 60 ? 0 : call.callRealMethod())
                    .when(source).extract(eq(0), any(), anyInt(), any());
            var cache = FilterItemData.createReadCache();
            var filters = new ItemStack[]{ItemStack.EMPTY};
            var ref = new TransferEngine.ItemTransferTarget(target.inventory, filters, FilterMode.MATCH_ANY,
                    TransferAmountRules.collect(filters, filters, cache), false, null, false);
            int moved = TransferEngine.commitSingleMove(source, ref,
                    new TransferPlan.MoveIntent(0, 0, ItemResource.of(Items.IRON_INGOT), 8, null),
                    8, filters, FilterMode.MATCH_ANY, null, cache, Map.of());
            assertEquals(reject ? 0 : 4, moved);
            assertEquals(reject ? 8 : 4, source.getAmountAsInt(0));
            assertEquals(reject ? 60 : 64, target.inventory.getAmountAsInt(0));
            assertTrue(target.inventory.bulkCalls > 0);
            if (reject) {
                assertEquals(0, target.saves);
                assertEquals(0, target.changes);
                assertEquals(60, target.contents.inventory().stacks().get(0).getCount());
            }
        }
    }
}
