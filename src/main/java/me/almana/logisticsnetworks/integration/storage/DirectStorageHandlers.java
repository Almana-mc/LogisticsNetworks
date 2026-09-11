package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DirectStorageHandlers {

    private DirectStorageHandlers() {
    }

    public static IItemHandler exportItems(StorageEndpoint endpoint) {
        return new ItemHandler(endpoint, endpoint.exportableItems(), true);
    }

    public static IItemHandler importItems(StorageEndpoint endpoint) {
        return new ItemHandler(endpoint, endpoint.storedItems(), false);
    }

    public static IFluidHandler exportFluids(StorageEndpoint endpoint) {
        return new FluidHandler(endpoint, endpoint.exportableFluids());
    }

    public static IFluidHandler importFluids(StorageEndpoint endpoint) {
        return new FluidHandler(endpoint, endpoint.storedFluids());
    }

    public static boolean isDirect(Object handler) {
        return handler instanceof NetworkHandler;
    }

    public static int snapshotView(Object handler) {
        if (!(handler instanceof ItemHandler direct)) return 0;
        return direct.exporting ? 1 : 2;
    }

    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (!(handler instanceof ItemHandler direct)) return stack;
        return direct.insertItem(direct.getSlots() - 1, stack, simulate);
    }

    public static boolean shareNetwork(Object first, Object second) {
        Object firstIdentity = networkIdentity(first);
        return firstIdentity != null && firstIdentity == networkIdentity(second);
    }

    @Nullable
    public static Object networkIdentity(Object handler) {
        return handler instanceof NetworkHandler direct ? direct.endpoint().networkIdentity() : null;
    }

    private interface NetworkHandler {
        StorageEndpoint endpoint();
    }

    private static final class ItemHandler implements IItemHandler, NetworkHandler {
        private final StorageEndpoint endpoint;
        private final List<StorageEndpoint.StoredItem> exports;
        private final boolean exporting;

        private ItemHandler(StorageEndpoint endpoint, List<StorageEndpoint.StoredItem> exports, boolean exporting) {
            this.endpoint = endpoint;
            this.exports = exports;
            this.exporting = exporting;
        }

        @Override
        public StorageEndpoint endpoint() {
            return endpoint;
        }

        @Override
        public int getSlots() {
            return exports.size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (!validSlot(slot) || slot >= exports.size() || !endpoint.isValid()) return ItemStack.EMPTY;
            StorageEndpoint.StoredItem stored = exports.get(slot);
            return stored.stack().copyWithCount(clamp(stored.amount()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!validSlot(slot) || stack.isEmpty() || !endpoint.isValid()) return stack;
            long inserted = endpoint.insertItem(stack, stack.getCount(), simulate);
            int remaining = stack.getCount() - clampToRequest(inserted, stack.getCount());
            return remaining == 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!validSlot(slot) || slot >= exports.size() || amount <= 0 || !endpoint.isValid()) {
                return ItemStack.EMPTY;
            }
            StorageEndpoint.StoredItem stored = exports.get(slot);
            int requested = Math.min(amount, clamp(stored.amount()));
            long extracted = endpoint.extractItem(stored.stack(), requested, simulate);
            int moved = clampToRequest(extracted, requested);
            return moved == 0 ? ItemStack.EMPTY : stored.stack().copyWithCount(moved);
        }

        @Override
        public int getSlotLimit(int slot) {
            return validSlot(slot) ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return validSlot(slot) && !stack.isEmpty() && endpoint.isValid();
        }

        private boolean validSlot(int slot) {
            return slot >= 0 && slot < getSlots();
        }
    }

    private static final class FluidHandler implements IFluidHandler, NetworkHandler {
        private final StorageEndpoint endpoint;
        private final List<StorageEndpoint.StoredFluid> exports;

        private FluidHandler(StorageEndpoint endpoint, List<StorageEndpoint.StoredFluid> exports) {
            this.endpoint = endpoint;
            this.exports = exports;
        }

        @Override
        public StorageEndpoint endpoint() {
            return endpoint;
        }

        @Override
        public int getTanks() {
            return Math.max(1, exports.size());
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (!validTank(tank) || tank >= exports.size() || !endpoint.isValid()) return FluidStack.EMPTY;
            StorageEndpoint.StoredFluid stored = exports.get(tank);
            return stored.stack().copyWithAmount(clamp(stored.amount()));
        }

        @Override
        public int getTankCapacity(int tank) {
            return validTank(tank) ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return validTank(tank) && !stack.isEmpty() && endpoint.isValid();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !endpoint.isValid()) return 0;
            long inserted = endpoint.insertFluid(resource, resource.getAmount(), action.simulate());
            return clampToRequest(inserted, resource.getAmount());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !endpoint.isValid() || !canExport(resource)) return FluidStack.EMPTY;
            long extracted = endpoint.extractFluid(resource, resource.getAmount(), action.simulate());
            int moved = clampToRequest(extracted, resource.getAmount());
            return moved == 0 ? FluidStack.EMPTY : resource.copyWithAmount(moved);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0 || exports.isEmpty() || !endpoint.isValid()) return FluidStack.EMPTY;
            FluidStack resource = exports.get(0).stack().copyWithAmount(maxDrain);
            return drain(resource, action);
        }

        private boolean canExport(FluidStack requested) {
            for (StorageEndpoint.StoredFluid stored : exports) {
                if (FluidStack.isSameFluidSameComponents(stored.stack(), requested)) return true;
            }
            return false;
        }

        private boolean validTank(int tank) {
            return tank >= 0 && tank < getTanks();
        }
    }

    private static int clamp(long amount) {
        return (int) Math.min(Math.max(0, amount), Integer.MAX_VALUE);
    }

    private static int clampToRequest(long amount, int request) {
        return (int) Math.min(Math.max(0, amount), request);
    }
}
