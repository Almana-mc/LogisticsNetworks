package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferEngineCommitTest extends SnapshotFixture {
    static final ItemStack[] NONE = {ItemStack.EMPTY};

    @Test
    void commitsOnlyActualAcceptance() {
        var source = inventory(20);
        var target = inventory(60);
        assertEquals(4, commit(source, target, new TransferPlan.MoveIntent(0, 0, IRON, 8, null)));
        assertEquals(16, source.getAmountAsInt(0));
        assertEquals(64, target.getAmountAsInt(0));
    }

    @Test
    void rejectsMalformedDescriptorsAndMasks() {
        var source = inventory(20);
        var target = inventory(0);
        var named = IRON.toStack();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        for (var move : new TransferPlan.MoveIntent[]{
                new TransferPlan.MoveIntent(-1, 0, IRON, 8, null),
                new TransferPlan.MoveIntent(1, 0, IRON, 8, null),
                new TransferPlan.MoveIntent(0, 0, IRON, -1, null),
                new TransferPlan.MoveIntent(0, 0, ItemResource.EMPTY, 8, null),
                new TransferPlan.MoveIntent(0, 0, ItemResource.of(named), 8, null),
                new TransferPlan.MoveIntent(0, 0, IRON, 8, new boolean[]{false}),
                new TransferPlan.MoveIntent(0, 0, IRON, 8, new boolean[]{true, true})}) {
            assertEquals(0, commit(source, target, move));
        }
        assertEquals(20, source.getAmountAsInt(0));
        assertEquals(0, target.getAmountAsInt(0));
        assertEquals(0, commit(source, source, new TransferPlan.MoveIntent(0, 0, IRON, 8, null)));
    }

    @Test
    void rejectedMatchingExtractionRollsBackInsertion() {
        var source = spy(inventory(8));
        var target = inventory(0);
        doAnswer(call -> {
            if (target.getAmountAsInt(0) > 0) return 0;
            return call.callRealMethod();
        }).when(source).extract(eq(0), eq(IRON), anyInt(), any());
        assertEquals(0, commit(source, target, new TransferPlan.MoveIntent(0, 0, IRON, 8, null)));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.getAmountAsInt(0));
    }

    @Test
    void sourceResourceChangedByTargetCallbackAbortsBothSides() {
        var source = inventory(8);
        var target = spy(inventory(0));
        doAnswer(call -> {
            net.neoforged.neoforge.transfer.transaction.TransactionContext tx = call.getArgument(2);
            source.extract(0, IRON, 8, tx);
            source.insert(0, ItemResource.of(net.minecraft.world.item.Items.GOLD_INGOT), 8, tx);
            return call.callRealMethod();
        }).when(target).insert(eq(IRON), anyInt(), any());
        assertEquals(0, commit(source, target, new TransferPlan.MoveIntent(0, 0, IRON, 8, null)));
        assertEquals(IRON, source.getResource(0));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.getAmountAsInt(0));
    }

    static int commit(ResourceHandler<ItemResource> source, ResourceHandler<ItemResource> target,
            TransferPlan.MoveIntent move) {
        var cache = FilterItemData.createReadCache();
        var resolved = new TransferEngine.ItemTransferTarget(target, NONE, FilterMode.MATCH_ANY,
                TransferAmountRules.collect(NONE, NONE, cache), false, null, false);
        return TransferEngine.commitSingleMove(source, resolved, move, 64, NONE, FilterMode.MATCH_ANY,
                PlannerDifferentialTest.provider(), cache, Map.of());
    }
}
