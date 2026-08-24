package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferEngineCommitTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ThreadGuard.markServerThread();
    }

    @Test
    void movesOnlyLiveAcceptedAmount() {
        ItemStackHandler source = handler(new ItemStack(Items.IRON_INGOT, 20));
        ItemStackHandler target = handler(new ItemStack(Items.IRON_INGOT, 60));
        TransferPlan.ItemMove move = move(source.getStackInSlot(0), 8, null);

        int moved = TransferEngine.commitSingleMove(source, target, move, null);

        assertEquals(4, moved);
        assertEquals(16, source.getStackInSlot(0).getCount());
        assertEquals(64, target.getStackInSlot(0).getCount());
    }

    @Test
    void rejectsStaleItemComponents() {
        ItemStack live = new ItemStack(Items.IRON_INGOT, 20);
        live.set(DataComponents.CUSTOM_NAME, Component.literal("live"));
        ItemStack planned = live.copy();
        planned.set(DataComponents.CUSTOM_NAME, Component.literal("planned"));
        ItemStackHandler source = handler(live);
        ItemStackHandler target = new ItemStackHandler(1);

        int moved = TransferEngine.commitSingleMove(source, target, move(planned, 8, null), null);

        assertEquals(0, moved);
        assertEquals(20, source.getStackInSlot(0).getCount());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    @Test
    void appliesTargetSlotMask() {
        ItemStackHandler source = handler(new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler target = new ItemStackHandler(2);

        int moved = TransferEngine.commitSingleMove(
                source, target, move(source.getStackInSlot(0), 8, new boolean[] {false, true}), null);

        assertEquals(8, moved);
        assertTrue(target.getStackInSlot(0).isEmpty());
        assertEquals(8, target.getStackInSlot(1).getCount());
    }

    @Test
    void retriesTargetAfterRollbackRejection() {
        ItemStackHandler source = new ItemStackHandler(1) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler target = new ItemStackHandler(1) {
            private int executions;

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate || executions++ > 0) {
                    return super.insertItem(slot, stack, simulate);
                }
                ItemStack remainder = stack.copyWithCount(stack.getCount() - 3);
                super.insertItem(slot, stack.copyWithCount(3), false);
                return remainder;
            }
        };

        int moved = TransferEngine.commitSingleMove(
                source, target, move(source.getStackInSlot(0), 8, null), null);

        assertEquals(8, moved);
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertEquals(8, target.getStackInSlot(0).getCount());
    }

    @Test
    void bulkCommitUsesOneRealInsertion() throws ClassNotFoundException {
        ItemStackHandler source = handler(new ItemStack(Items.IRON_INGOT, 8));
        CountingBulkHandler target = new CountingBulkHandler(5);

        int moved = TransferEngine.commitSingleMove(
                source, target.handler, target.handler, move(source.getStackInSlot(0), 8, null), null);

        assertEquals(5, moved);
        assertEquals(0, target.simulations);
        assertEquals(1, target.executions);
        assertEquals(3, source.getStackInSlot(0).getCount());
        assertEquals(5, target.getStackInSlot(0).getCount());
        assertEquals(8, source.getStackInSlot(0).getCount() + target.getStackInSlot(0).getCount());
    }

    @Test
    void validatesExpectedComponentsAfterSourceSimulationBeforeExtraction() throws ClassNotFoundException {
        ItemStack live = new ItemStack(Items.IRON_INGOT, 8);
        live.set(DataComponents.CUSTOM_NAME, Component.literal("live"));
        ItemStack planned = live.copy();
        planned.set(DataComponents.CUSTOM_NAME, Component.literal("planned"));
        AccessTrackingHandler source = new AccessTrackingHandler();
        source.setStackInSlot(0, live);
        CountingBulkHandler target = new CountingBulkHandler(8);

        int moved = TransferEngine.commitSingleMove(source, target.handler, target.handler, move(planned, 8, null), null);

        assertEquals(0, moved);
        assertEquals(1, source.simulations);
        assertEquals(0, source.executions);
        assertEquals(0, target.simulations + target.executions);
        assertEquals(8, source.getStackInSlot(0).getCount());
    }

    @Test
    void bulkCommitReturnsPartialRemainderToOtherSourceSlot() throws ClassNotFoundException {
        ItemStackHandler source = new ItemStackHandler(2) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return slot == 0 ? stack : super.insertItem(slot, stack, simulate);
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        CountingBulkHandler target = new CountingBulkHandler(3);

        int moved = TransferEngine.commitSingleMove(
                source, target.handler, target.handler, move(source.getStackInSlot(0), 8, null), null);

        assertEquals(3, moved);
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertEquals(5, source.getStackInSlot(1).getCount());
        assertEquals(3, target.getStackInSlot(0).getCount());
        assertEquals(8, source.getStackInSlot(1).getCount() + target.getStackInSlot(0).getCount());
    }

    @Test
    void maskedTargetRetainsLiveSimulation() {
        ItemStackHandler source = handler(new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler delegate = new ItemStackHandler(2);
        int[] simulations = {0};
        IItemHandler target = new IItemHandler() {
            @Override
            public int getSlots() {
                return delegate.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return delegate.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) {
                    simulations[0]++;
                }
                return delegate.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return delegate.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return delegate.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return delegate.isItemValid(slot, stack);
            }
        };

        int moved = TransferEngine.commitSingleMove(
                source, target, move(source.getStackInSlot(0), 8, new boolean[] {false, true}), null);

        assertEquals(8, moved);
        assertEquals(8, delegate.getStackInSlot(1).getCount());
        assertEquals(1, simulations[0]);
    }

    @Test
    void offServerThreadRejectedBeforeHandlerAccess() throws Exception {
        AccessTrackingHandler source = new AccessTrackingHandler();
        ItemStackHandler target = new ItemStackHandler(1);
        TransferPlan.ItemMove move = new TransferPlan.ItemMove(
                0, 0, Items.IRON_INGOT, ItemStack.EMPTY.getComponents(), 1, null);
        FutureTask<Void> task = new FutureTask<>(() -> {
            assertThrows(IllegalStateException.class,
                    () -> TransferEngine.commitSingleMove(source, target, move, null));
            return null;
        });

        Thread worker = new Thread(task);
        worker.start();
        worker.join();
        task.get();

        assertEquals(0, source.reads);
    }

    private static ItemStackHandler handler(ItemStack stack) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, stack);
        return handler;
    }

    private static TransferPlan.ItemMove move(ItemStack expected, int amount, boolean[] mask) {
        return new TransferPlan.ItemMove(
                0, 0, expected.getItem(), expected.getComponents(), amount, mask);
    }

    private static final class AccessTrackingHandler extends ItemStackHandler {

        private int reads;
        private int simulations;
        private int executions;

        private AccessTrackingHandler() {
            super(1);
        }

        @Override
        public int getSlots() {
            reads++;
            return super.getSlots();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            reads++;
            if (simulate) {
                simulations++;
            } else {
                executions++;
            }
            return super.extractItem(slot, amount, simulate);
        }
    }

    private static final class CountingBulkHandler {

        private final int acceptedPerExecution;
        private final ItemStackHandler delegate = new ItemStackHandler(1);
        private final IItemHandler handler;
        private int simulations;
        private int executions;

        private CountingBulkHandler(int acceptedPerExecution) throws ClassNotFoundException {
            this.acceptedPerExecution = acceptedPerExecution;
            Class<?> simpleInserter = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.inventory.IItemHandlerSimpleInserter");
            handler = (IItemHandler) Proxy.newProxyInstance(simpleInserter.getClassLoader(),
                    new Class<?>[] {IItemHandler.class, simpleInserter}, this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getName().equals("insertItem") && method.getParameterCount() == 2) {
                ItemStack stack = (ItemStack) arguments[0];
                boolean simulate = (boolean) arguments[1];
                if (simulate) {
                    simulations++;
                    return ItemStack.EMPTY;
                }
                executions++;
                int accepted = Math.min(acceptedPerExecution, stack.getCount());
                delegate.setStackInSlot(0, stack.copyWithCount(accepted));
                return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
            }
            return method.invoke(delegate, arguments);
        }

        private ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }
    }
}
