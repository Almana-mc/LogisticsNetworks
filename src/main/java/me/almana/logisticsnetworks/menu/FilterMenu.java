package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public class FilterMenu extends AbstractContainerMenu {

    private static final int MSG_BLACKLIST = 0;
    private static final int MSG_AMOUNT = 1;
    private static final int MSG_DEC_64 = 1;

    private static final int ID_TOGGLE_MODE = 0;
    private static final int ID_CYCLE_DURABILITY = 7;
    private static final int ID_CYCLE_TARGET = 8;

    private static final int FILTER_COLS = 9;
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 20;

    private final InteractionHand hand;
    private final Player player;
    private final int slotCount;
    private final int rows;

    private final boolean isTagMode;
    private final boolean isAmountMode;
    private final boolean isNbtMode;
    private final boolean isDurabilityMode;
    private final boolean isModMode;
    private final boolean isSlotMode;
    private final boolean isSpecialMode;

    private final SimpleContainer filterInventory;
    private final SimpleContainer extractorInventory = new SimpleContainer(1);
    private final ContainerData data = new SimpleContainerData(2);
    private final int lockedSlot;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    // Tracks which slots are storing Fluids vs Items
    private final boolean[] isFluidSlot;
    private boolean ignoreUpdates = false;

    private String selectedTag;
    private String selectedMod;

    public FilterMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(Registration.FILTER_MENU.get(), containerId);
        this.hand = hand;
        this.player = playerInv.player;
        this.lockedSlot = (hand == InteractionHand.MAIN_HAND) ? playerInv.selected : -1;

        ItemStack stack = getOpenedStack();
        this.isTagMode = stack.getItem() instanceof TagFilterItem;
        this.isAmountMode = stack.getItem() instanceof AmountFilterItem;
        this.isNbtMode = stack.getItem() instanceof NbtFilterItem;
        this.isDurabilityMode = stack.getItem() instanceof DurabilityFilterItem;
        this.isModMode = stack.getItem() instanceof ModFilterItem;
        this.isSlotMode = stack.getItem() instanceof SlotFilterItem;
        this.isSpecialMode = isTagMode || isAmountMode || isNbtMode || isDurabilityMode || isModMode || isSlotMode;

        this.slotCount = isSpecialMode ? 0 : Math.max(1, FilterItemData.getCapacity(stack));
        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];

        initSyncedData(stack);
        layoutSlots(playerInv);
        addDataSlots(data);
    }

    public FilterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.FILTER_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        this.hand = (handOrdinal >= 0 && handOrdinal < InteractionHand.values().length)
                ? InteractionHand.values()[handOrdinal]
                : InteractionHand.MAIN_HAND;
        this.player = playerInv.player;
        this.lockedSlot = (hand == InteractionHand.MAIN_HAND) ? playerInv.selected : -1;

        this.slotCount = Math.max(0, buf.readVarInt());

        this.isTagMode = buf.readBoolean();
        this.isAmountMode = buf.readBoolean();
        this.isNbtMode = buf.readBoolean();
        this.isDurabilityMode = buf.readBoolean();
        this.isModMode = buf.readBoolean();
        this.isSlotMode = buf.readBoolean();
        this.isSpecialMode = isTagMode || isAmountMode || isNbtMode || isDurabilityMode || isModMode || isSlotMode;

        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];

        layoutSlots(playerInv);
        addDataSlots(data);
    }

    private void initSyncedData(ItemStack stack) {
        HolderLookup.Provider provider = player.level().registryAccess();

        if (isTagMode) {
            data.set(0, TagFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, TagFilterData.getTargetType(stack).ordinal());
            var tags = TagFilterData.getTagFilters(stack);
            selectedTag = tags.isEmpty() ? null : tags.get(0);
        } else if (isModMode) {
            data.set(0, ModFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, ModFilterData.getTargetType(stack).ordinal());
            var mods = ModFilterData.getModFilters(stack);
            selectedMod = mods.isEmpty() ? null : mods.get(0);
        } else if (isNbtMode) {
            data.set(0, NbtFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, NbtFilterData.getTargetType(stack).ordinal());
        } else if (isAmountMode) {
            data.set(0, AmountFilterData.getAmount(stack));
            data.set(1, 0);
        } else if (isSlotMode) {
            data.set(0, SlotFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, 0);
        } else if (isDurabilityMode) {
            data.set(0, DurabilityFilterData.getValue(stack));
            data.set(1, DurabilityFilterData.getOperator(stack).ordinal());
        } else {
            loadFilterItems(stack, provider);
            data.set(0, FilterItemData.isBlacklist(stack) ? 1 : 0);
        }
    }

    private void layoutSlots(Inventory playerInv) {
        if (!isSpecialMode) {
            for (int i = 0; i < slotCount; i++) {
                int r = i / FILTER_COLS;
                int c = i % FILTER_COLS;
                addSlot(new GhostSlot(filterInventory, i, FILTER_X + c * 18, FILTER_Y + r * 18));
            }
        }

        if (isTagMode || isNbtMode || isModMode) {
            int y = FILTER_Y + 14;
            addSlot(new GhostSlot(extractorInventory, 0, FILTER_X, y));
        }

        int playerY = getPlayerInventoryY();
        playerSlotStart = slots.size();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new PlayerSlot(playerInv, c + r * 9 + 9, 8 + c * 18, playerY + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new PlayerSlot(playerInv, c, 8 + c * 18, playerY + 58));
        }
        playerSlotEnd = slots.size();
    }

    public boolean isBlacklistMode() {
        return data.get(0) == 1;
    }

    public FilterTargetType getTargetType() {
        return FilterTargetType.fromOrdinal(data.get(1));
    }

    // Amount Mode
    public int getAmount() {
        return data.get(0);
    }

    // Durability Mode
    public int getDurabilityValue() {
        return data.get(0);
    }

    public DurabilityFilterData.Operator getDurabilityOperator() {
        int idx = Math.max(0, Math.min(DurabilityFilterData.Operator.values().length - 1, data.get(1)));
        return DurabilityFilterData.Operator.values()[idx];
    }

    public String getSelectedTag() {
        if (selectedTag == null && isTagMode) {
            var tags = TagFilterData.getTagFilters(getOpenedStack());
            if (!tags.isEmpty()) {
                selectedTag = tags.get(0);
            }
        }
        return selectedTag;
    }

    public void setSelectedTag(String tag) {
        this.selectedTag = (tag == null || tag.isBlank()) ? null : tag.trim();
    }

    public String getSelectedMod() {
        if (selectedMod == null && isModMode) {
            var mods = ModFilterData.getModFilters(getOpenedStack());
            if (!mods.isEmpty()) {
                selectedMod = mods.get(0);
            }
        }
        return selectedMod;
    }

    public void setSelectedMod(String mod) {
        this.selectedMod = (mod == null || mod.isBlank()) ? null : mod.trim();
    }

    public String getSelectedNbtPath() {
        return NbtFilterData.getSelectedPath(getOpenedStack());
    }

    public void setSelectedNbtPath(String path) {
        NbtFilterData.setSelection(getOpenedStack(), path, null);
    }

    public String getSelectedNbtValue() {
        return NbtFilterData.getSelectedValueDisplay(getOpenedStack());
    }

    public boolean isTagMode() {
        return isTagMode;
    }

    public boolean isModMode() {
        return isModMode;
    }

    public boolean isNbtMode() {
        return isNbtMode;
    }

    public boolean isAmountMode() {
        return isAmountMode;
    }

    public boolean isDurabilityMode() {
        return isDurabilityMode;
    }

    public boolean isSlotMode() {
        return isSlotMode;
    }

    public String getSlotExpression() {
        if (!isSlotMode) {
            return "";
        }
        return SlotFilterData.getSlotExpression(getOpenedStack());
    }

    public int getFilterSlots() {
        return slotCount;
    }

    public int getRows() {
        return rows;
    }

    public int getPlayerInventoryY() {
        return 82 + (isSpecialMode ? 40 : rows * 18);
    }

    public boolean isPlayerInventorySlot(int menuSlotIndex) {
        return menuSlotIndex >= playerSlotStart && menuSlotIndex < playerSlotEnd;
    }

    public int getExtractorSlotIndex() {
        return (isTagMode || isNbtMode || isModMode) ? slotCount : -1;
    }

    public ItemStack getExtractorItem() {
        return (isTagMode || isNbtMode || isModMode) ? extractorInventory.getItem(0) : ItemStack.EMPTY;
    }

    public FluidStack getFluidFilter(int slot) {
        return FilterItemData.getFluidEntry(getOpenedStack(), slot);
    }

    public void setAmountValue(Player player, int amount) {
        if (isAmountMode) {
            ItemStack stack = getOpenedStack();
            AmountFilterData.setAmount(stack, amount);
            data.set(0, AmountFilterData.getAmount(stack));
            broadcastChanges();
        }
    }

    public void setDurabilityValue(Player player, int value) {
        if (isDurabilityMode) {
            ItemStack stack = getOpenedStack();
            DurabilityFilterData.setValue(stack, value);
            data.set(0, DurabilityFilterData.getValue(stack));
            broadcastChanges();
        }
    }

    public boolean setSlotExpression(Player player, String expression) {
        if (!isSlotMode) {
            return false;
        }

        SlotFilterData.ParseResult result = SlotFilterData.setSlotsFromExpression(getOpenedStack(), expression);
        if (!result.valid()) {
            return false;
        }
        if (result.changed()) {
            broadcastChanges();
        }
        return true;
    }

    public ItemStack getOpenedFilterStack(Player player) {
        return getOpenedStack();
    }

    public ItemStack getOpenedStack() {
        if (hand == InteractionHand.OFF_HAND)
            return player.getOffhandItem();
        return (lockedSlot >= 0) ? player.getInventory().getItem(lockedSlot) : player.getMainHandItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide)
            return false;

        if (id == ID_TOGGLE_MODE)
            return toggleBlacklist();
        if (isDurabilityMode)
            return handleDurabilityAction(id);
        if (isAmountMode)
            return handleAmountAction(id);
        if ((isTagMode || isModMode || isNbtMode) && id == ID_CYCLE_TARGET)
            return cycleTargetType();

        return false;
    }

    private boolean toggleBlacklist() {
        boolean newState = data.get(0) == 0;
        data.set(0, newState ? 1 : 0);

        ItemStack stack = getOpenedStack();
        if (isTagMode)
            TagFilterData.setBlacklist(stack, newState);
        else if (isModMode)
            ModFilterData.setBlacklist(stack, newState);
        else if (isNbtMode)
            NbtFilterData.setBlacklist(stack, newState);
        else if (isSlotMode)
            SlotFilterData.setBlacklist(stack, newState);
        else
            FilterItemData.setBlacklist(stack, newState);

        broadcastChanges();
        return true;
    }

    private boolean cycleTargetType() {
        FilterTargetType next = getTargetType().next();
        data.set(1, next.ordinal());

        ItemStack stack = getOpenedStack();
        if (isTagMode)
            TagFilterData.setTargetType(stack, next);
        else if (isModMode)
            ModFilterData.setTargetType(stack, next);
        else if (isNbtMode)
            NbtFilterData.setTargetType(stack, next);

        broadcastChanges();
        return true;
    }

    private boolean handleDurabilityAction(int id) {
        if (id == ID_CYCLE_DURABILITY) {
            ItemStack stack = getOpenedStack();
            var next = DurabilityFilterData.getOperator(stack).next();
            DurabilityFilterData.setOperator(stack, next);
            data.set(1, next.ordinal());
            broadcastChanges();
            return true;
        }

        int delta = getDelta(id);
        if (delta != 0) {
            int current = data.get(0);
            int next = Math.max(0, Math.min(100, current + delta));
            DurabilityFilterData.setValue(getOpenedStack(), next);
            data.set(0, next);
            broadcastChanges();
            return true;
        }
        return false;
    }

    private boolean handleAmountAction(int id) {
        int delta = getDelta(id);
        if (delta != 0) {
            int current = data.get(0);
            int next = Math.max(0, current + delta);
            AmountFilterData.setAmount(getOpenedStack(), next);
            data.set(0, next);
            broadcastChanges();
            return true;
        }
        return false;
    }

    private int getDelta(int id) {
        return switch (id) {
            case 1 -> -64;
            case 2 -> -10;
            case 3 -> -1;
            case 4 -> 1;
            case 5 -> 10;
            case 6 -> 64;
            default -> 0;
        };
    }

    // -- Interaction Logic --

    public boolean setFluidFilterEntry(Player player, int slot, FluidStack fluid) {
        if (isSpecialMode || slot < 0 || slot >= slotCount || fluid.isEmpty())
            return false;

        updateFilter(slot, s -> {
            FilterItemData.setFluidEntry(getOpenedStack(), s, fluid);
            isFluidSlot[s] = true;
            // Clear the visual item in the container to avoid confusion,
            // but we need to suppress the event to avoid infinite loop or clearing data
            ignoreUpdates = true;
            filterInventory.setItem(s, ItemStack.EMPTY);
            ignoreUpdates = false;
        });
        return true;
    }

    public boolean setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (isSpecialMode || slot < 0 || slot >= slotCount || stack.isEmpty() || stack.is(ModTags.FILTERS))
            return false;

        ItemStack itemEntry = stack.copyWithCount(1);
        updateFilter(slot, s -> {
            FilterItemData.setEntry(getOpenedStack(), s, itemEntry, player.level().registryAccess());
            isFluidSlot[s] = false;
            filterInventory.setItem(s, itemEntry);
        });
        return true;
    }

    private void clearFilterEntry(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;

        updateFilter(slot, s -> {
            ItemStack stack = getOpenedStack();
            FilterItemData.setEntry(stack, s, ItemStack.EMPTY, player.level().registryAccess());
            FilterItemData.setFluidEntry(stack, s, FluidStack.EMPTY);
            isFluidSlot[s] = false;
            filterInventory.setItem(s, ItemStack.EMPTY);
        });
    }

    private void updateFilter(int slot, java.util.function.IntConsumer action) {
        action.accept(slot);
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // Special logic for "Ghost" slots - we don't allow normal pickup/place
        // We intercept clicks to update the filter settings instead
        if (clickType == ClickType.PICKUP && slotId >= 0 && slotId < slots.size()) {

            // Standard Filter Grids
            if (!isSpecialMode && slotId < slotCount) {
                handleGhostGridClick(player, slotId, dragType);
                return;
            }

            // Extractor Slot
            if ((isTagMode || isNbtMode || isModMode) && slotId == getExtractorSlotIndex()) {
                handleExtractorClick(player, dragType);
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private void handleGhostGridClick(Player player, int slotId, int interactionMode) {
        ItemStack held = getCarried();

        if (held.isEmpty()) {
            clearFilterEntry(slotId);
            return;
        }

        if (held.is(ModTags.FILTERS))
            return;

        // Right-click tries to extract fluid first
        if (interactionMode == 1) {
            FluidStack fluid = getFluidFromItem(held);
            if (!fluid.isEmpty()) {
                setFluidFilterEntry(player, slotId, fluid);
                return;
            }
        }

        setItemFilterEntry(player, slotId, held);
    }

    private void handleExtractorClick(Player player, int interactionMode) {
        ItemStack held = getCarried();

        if (held.isEmpty()) {
            extractorInventory.setItem(0, ItemStack.EMPTY);
            broadcastChanges();
            return;
        }

        if (held.is(ModTags.FILTERS))
            return;

        if (interactionMode == 1) {
            FluidStack fluid = getFluidFromItem(held);
            if (!fluid.isEmpty()) {
                extractorInventory.setItem(0, held.copyWithCount(1));
                broadcastChanges();
                return;
            }
        }

        extractorInventory.setItem(0, held.copyWithCount(1));
        broadcastChanges();
    }

    private FluidStack getFluidFromItem(ItemStack stack) {
        // 1. Capability/Data check
        var contained = FluidUtil.getFluidContained(stack);
        if (contained.isPresent() && !contained.get().isEmpty()) {
            return contained.get();
        }

        // 2. Fallback to bucket registry check for vanilla items
        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid.getBucket() == stack.getItem()) {
                return new FluidStack(fluid, 1000);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking in a filter GUI usually implies setting a filter,
        // but since these are ghost slots, moving valid items INTO them isn't standard
        // container logic.
        // It's safer to block it or implement custom "fill next empty filter" logic.
        // For now, we return empty to block shift-move.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getOpenedStack();
        return !stack.isEmpty() && (stack.getItem() instanceof BaseFilterItem ||
                stack.getItem() instanceof TagFilterItem ||
                stack.getItem() instanceof AmountFilterItem ||
                stack.getItem() instanceof NbtFilterItem ||
                stack.getItem() instanceof DurabilityFilterItem ||
                stack.getItem() instanceof ModFilterItem ||
                stack.getItem() instanceof SlotFilterItem);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && !isSpecialMode) {
            saveFilterItems(getOpenedStack(), player.level().registryAccess());
        }
    }

    // -- Persistence --

    private void loadFilterItems(ItemStack stack, HolderLookup.Provider provider) {
        for (int i = 0; i < slotCount; i++) {
            FluidStack fluid = FilterItemData.getFluidEntry(stack, i);
            if (!fluid.isEmpty()) {
                isFluidSlot[i] = true;
                filterInventory.setItem(i, ItemStack.EMPTY);
            } else {
                isFluidSlot[i] = false;
                filterInventory.setItem(i, FilterItemData.getEntry(stack, i, provider));
            }
        }
    }

    private void saveFilterItems(ItemStack stack, HolderLookup.Provider provider) {
        FilterItemData.setBlacklist(stack, isBlacklistMode());
        for (int i = 0; i < slotCount; i++) {
            if (isFluidSlot[i]) {
                FluidStack fluid = FilterItemData.getFluidEntry(stack, i);
                FilterItemData.setFluidEntry(stack, i, fluid);
            } else {
                FilterItemData.setEntry(stack, i, filterInventory.getItem(i), provider);
            }
        }
    }

    // -- Slot Classes --

    private class GhostSlot extends Slot {
        public GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            if (!ignoreUpdates && getSlotIndex() >= 0 && getSlotIndex() < isFluidSlot.length) {
                // If the item changed via some mechanism, assume it's no longer a liquid ghost
                // unless we are suppressing (e.g. during load)
                isFluidSlot[getSlotIndex()] = false;
            }
            super.setChanged();
        }
    }

    private class PlayerSlot extends Slot {
        private final int index;

        public PlayerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPickup(Player player) {
            return index != lockedSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return index != lockedSlot;
        }
    }
}
