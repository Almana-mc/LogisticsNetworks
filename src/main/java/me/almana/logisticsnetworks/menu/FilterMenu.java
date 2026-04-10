package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import net.minecraft.nbt.Tag;
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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import javax.annotation.Nullable;
import java.util.List;

public class FilterMenu extends AbstractContainerMenu {

    private static final int MSG_BLACKLIST = 0;
    private static final int MSG_AMOUNT = 1;
    private static final int MSG_DEC_64 = 1;

    private static final int ID_TOGGLE_MODE = 0;
    private static final int ID_CYCLE_DURABILITY = 7;
    private static final int ID_CYCLE_TARGET = 8;
    private static final int ID_CYCLE_NAME_SCOPE = 9;

    private static final int FILTER_COLS = 9;
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 34;

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
    private final boolean isNameMode;
    private final boolean isSpecialMode;

    private final SimpleContainer filterInventory;
    private final SimpleContainer extractorInventory = new SimpleContainer(1);
    private final ContainerData data = new SimpleContainerData(4);
    private final int lockedSlot;
    private final int inventorySlotIndex;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    private final boolean[] isFluidSlot;
    private final boolean[] isChemicalSlot;
    private final boolean[] isTagSlot;
    private boolean ignoreUpdates = false;

    @Nullable
    private final LogisticsNodeEntity nodeSource;
    private final int nodeChannel;
    private final int nodeFilterSlot;

    private String selectedTag;
    private String selectedMod;

    public static void writeMenuData(FriendlyByteBuf buf, InteractionHand hand, int slotCount,
            boolean isMod, boolean isSlot, boolean isName) {
        buf.writeVarInt(hand.ordinal());
        writeModeData(buf, slotCount, isMod, isSlot, isName);
    }

    public static void writeMenuData(FriendlyByteBuf buf, int inventorySlotIndex, int slotCount,
            boolean isMod, boolean isSlot, boolean isName) {
        buf.writeVarInt(-1);
        buf.writeVarInt(inventorySlotIndex);
        writeModeData(buf, slotCount, isMod, isSlot, isName);
    }

    private static void writeModeData(FriendlyByteBuf buf, int slotCount,
            boolean isMod, boolean isSlot, boolean isName) {
        buf.writeVarInt(slotCount);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(isMod);
        buf.writeBoolean(isSlot);
        buf.writeBoolean(isName);
    }

    public FilterMenu(int containerId, Inventory playerInv, int inventorySlotIndex) {
        super(Registration.FILTER_MENU.get(), containerId);
        this.hand = InteractionHand.MAIN_HAND;
        this.player = playerInv.player;
        this.inventorySlotIndex = inventorySlotIndex;
        this.lockedSlot = inventorySlotIndex;
        this.nodeSource = null;
        this.nodeChannel = -1;
        this.nodeFilterSlot = -1;

        ItemStack stack = getOpenedStack();
        this.isTagMode = false;
        this.isAmountMode = false;
        this.isNbtMode = false;
        this.isDurabilityMode = false;
        this.isModMode = stack.getItem() instanceof ModFilterItem;
        this.isSlotMode = stack.getItem() instanceof SlotFilterItem;
        this.isNameMode = stack.getItem() instanceof NameFilterItem;
        this.isSpecialMode = isModMode || isSlotMode || isNameMode;

        this.slotCount = isSpecialMode ? 0 : Math.max(1, FilterItemData.getCapacity(stack));
        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];
        this.isChemicalSlot = new boolean[slotCount];
        this.isTagSlot = new boolean[slotCount];

        initSyncedData(stack);
        layoutSlots(playerInv);
        addDataSlots(data);
    }

    public FilterMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(Registration.FILTER_MENU.get(), containerId);
        this.hand = hand;
        this.player = playerInv.player;
        this.inventorySlotIndex = -1;
        this.lockedSlot = (hand == InteractionHand.MAIN_HAND) ? playerInv.getSelectedSlot() : -1;
        this.nodeSource = null;
        this.nodeChannel = -1;
        this.nodeFilterSlot = -1;

        ItemStack stack = getOpenedStack();
        this.isTagMode = false;
        this.isAmountMode = false;
        this.isNbtMode = false;
        this.isDurabilityMode = false;
        this.isModMode = stack.getItem() instanceof ModFilterItem;
        this.isSlotMode = stack.getItem() instanceof SlotFilterItem;
        this.isNameMode = stack.getItem() instanceof NameFilterItem;
        this.isSpecialMode = isModMode || isSlotMode || isNameMode;

        this.slotCount = isSpecialMode ? 0 : Math.max(1, FilterItemData.getCapacity(stack));
        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];
        this.isChemicalSlot = new boolean[slotCount];
        this.isTagSlot = new boolean[slotCount];

        initSyncedData(stack);
        layoutSlots(playerInv);
        addDataSlots(data);
    }

    public FilterMenu(int containerId, Inventory playerInv, LogisticsNodeEntity node, int channel, int filterSlot) {
        super(Registration.FILTER_MENU.get(), containerId);
        this.hand = InteractionHand.MAIN_HAND;
        this.player = playerInv.player;
        this.inventorySlotIndex = -1;
        this.lockedSlot = -1;
        this.nodeSource = node;
        this.nodeChannel = channel;
        this.nodeFilterSlot = filterSlot;

        ItemStack stack = getOpenedStack();
        this.isTagMode = false;
        this.isAmountMode = false;
        this.isNbtMode = false;
        this.isDurabilityMode = false;
        this.isModMode = stack.getItem() instanceof ModFilterItem;
        this.isSlotMode = stack.getItem() instanceof SlotFilterItem;
        this.isNameMode = stack.getItem() instanceof NameFilterItem;
        this.isSpecialMode = isTagMode || isAmountMode || isDurabilityMode || isModMode || isSlotMode
                || isNameMode;

        this.slotCount = isSpecialMode ? 0 : Math.max(1, FilterItemData.getCapacity(stack));
        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];
        this.isChemicalSlot = new boolean[slotCount];
        this.isTagSlot = new boolean[slotCount];

        initSyncedData(stack);
        layoutSlots(playerInv);
        addDataSlots(data);
    }

    public FilterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.FILTER_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        if (handOrdinal == -2) {
            int entityId = buf.readVarInt();
            this.nodeChannel = buf.readVarInt();
            this.nodeFilterSlot = buf.readVarInt();
            this.inventorySlotIndex = -1;
            this.hand = InteractionHand.MAIN_HAND;
            this.lockedSlot = -1;
            var entity = playerInv.player.level().getEntity(entityId);
            this.nodeSource = (entity instanceof LogisticsNodeEntity node) ? node : null;
        } else if (handOrdinal == -1) {
            this.inventorySlotIndex = buf.readVarInt();
            this.hand = InteractionHand.MAIN_HAND;
            this.lockedSlot = inventorySlotIndex;
            this.nodeSource = null;
            this.nodeChannel = -1;
            this.nodeFilterSlot = -1;
        } else {
            this.inventorySlotIndex = -1;
            this.hand = (handOrdinal >= 0 && handOrdinal < InteractionHand.values().length)
                    ? InteractionHand.values()[handOrdinal]
                    : InteractionHand.MAIN_HAND;
            this.lockedSlot = (hand == InteractionHand.MAIN_HAND) ? playerInv.getSelectedSlot() : -1;
            this.nodeSource = null;
            this.nodeChannel = -1;
            this.nodeFilterSlot = -1;
        }
        this.player = playerInv.player;

        this.slotCount = Math.max(0, buf.readVarInt());

        this.isTagMode = false;
        this.isAmountMode = false;
        buf.readBoolean();
        this.isNbtMode = false;
        this.isDurabilityMode = false;
        buf.readBoolean();
        this.isModMode = buf.readBoolean();
        this.isSlotMode = buf.readBoolean();
        this.isNameMode = buf.readBoolean();
        this.isSpecialMode = isModMode || isSlotMode || isNameMode;

        this.rows = isSpecialMode ? 0 : (int) Math.ceil(slotCount / 9.0);
        this.filterInventory = new SimpleContainer(slotCount);
        this.isFluidSlot = new boolean[slotCount];
        this.isChemicalSlot = new boolean[slotCount];
        this.isTagSlot = new boolean[slotCount];

        layoutSlots(playerInv);
        addDataSlots(data);

        if (!isSpecialMode) {
            loadFilterItems(getOpenedStack(), player.level().registryAccess());
        }
    }

    private void initSyncedData(ItemStack stack) {
        HolderLookup.Provider provider = player.level().registryAccess();

        if (isModMode) {
            data.set(0, ModFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, ModFilterData.getTargetType(stack).ordinal());
            data.set(2, 0);
            var mods = ModFilterData.getModFilters(stack);
            selectedMod = mods.isEmpty() ? null : mods.get(0);
        } else if (isNameMode) {
            data.set(0, NameFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, NameFilterData.getTargetType(stack).ordinal());
            data.set(2, NameFilterData.getMatchScope(stack).ordinal());
        } else if (isSlotMode) {
            data.set(0, SlotFilterData.isBlacklist(stack) ? 1 : 0);
            data.set(1, 0);
            data.set(2, 0);
        } else {
            loadFilterItems(stack, provider);
            data.set(0, FilterItemData.isBlacklist(stack) ? 1 : 0);
            data.set(1, FilterItemData.getTargetType(stack).ordinal());
            data.set(2, 0);
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

        if (isModMode) {
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
        return data.get(2);
    }

    // Durability Mode
    public int getDurabilityValue() {
        return data.get(2);
    }

    public DurabilityFilterData.Operator getDurabilityOperator() {
        int idx = Math.max(0, Math.min(DurabilityFilterData.Operator.values().length - 1, data.get(3)));
        return DurabilityFilterData.Operator.values()[idx];
    }

    public String getSelectedTag() {
        return selectedTag;
    }

    public void setSelectedTag(String tag) {
        this.selectedTag = FilterTagUtil.normalizeTag(tag);
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

    public boolean isTagMode() {
        return false;
    }

    public boolean isModMode() {
        return isModMode;
    }

    public boolean isNbtMode() {
        return false;
    }

    public boolean isAmountMode() {
        return false;
    }

    public boolean isDurabilityMode() {
        return false;
    }

    public boolean isSlotMode() {
        return isSlotMode;
    }

    public boolean isNameMode() {
        return isNameMode;
    }

    public boolean isNodeFilter() {
        return nodeSource != null;
    }

    @Nullable
    public LogisticsNodeEntity getNodeSource() {
        return nodeSource;
    }

    public int getNodeChannel() {
        return nodeChannel;
    }

    public int getNodeFilterSlot() {
        return nodeFilterSlot;
    }

    public String getNameFilter() {
        if (!isNameMode)
            return "";
        return NameFilterData.getNameFilter(getOpenedStack());
    }

    public void setNameFilter(String name) {
        if (!isNameMode)
            return;
        NameFilterData.setNameFilter(getOpenedStack(), name);
    }

    public boolean setNameExpression(Player player, String name) {
        if (!isNameMode)
            return false;
        NameFilterData.setNameFilter(getOpenedStack(), name);
        broadcastChanges();
        return true;
    }

    public NameMatchScope getNameMatchScope() {
        if (!isNameMode)
            return NameMatchScope.NAME;
        return NameMatchScope.fromOrdinal(data.get(2));
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
        return isSpecialMode ? 122 : 96 + rows * 18;
    }

    public boolean isPlayerInventorySlot(int menuSlotIndex) {
        return menuSlotIndex >= playerSlotStart && menuSlotIndex < playerSlotEnd;
    }

    public int getExtractorSlotIndex() {
        return (isTagMode || isModMode) ? slotCount : -1;
    }

    public ItemStack getExtractorItem() {
        return (isTagMode || isModMode) ? extractorInventory.getItem(0) : ItemStack.EMPTY;
    }

    public FluidStack getFluidFilter(int slot) {
        return FilterItemData.getFluidEntry(getOpenedStack(), slot);
    }

    public String getChemicalFilter(int slot) {
        return FilterItemData.getChemicalEntry(getOpenedStack(), slot);
    }

    public int getEntryAmount(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return 0;
        return FilterItemData.getEntryAmount(getOpenedStack(), slot);
    }

    public void setEntryAmount(Player player, int slot, int amount) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryAmount(getOpenedStack(), slot, Math.max(0, amount));
        broadcastChanges();
    }

    public int getEntryBatch(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return 0;
        return FilterItemData.getEntryBatch(getOpenedStack(), slot);
    }

    public void setEntryBatch(Player player, int slot, int batch) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryBatch(getOpenedStack(), slot, Math.max(0, batch));
        broadcastChanges();
    }

    public int getEntryStock(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return 0;
        return FilterItemData.getEntryStock(getOpenedStack(), slot);
    }

    public void setEntryStock(Player player, int slot, int stock) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryStock(getOpenedStack(), slot, Math.max(0, stock));
        broadcastChanges();
    }

    @Nullable
    public Boolean getEntryEnchanted(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return null;
        return FilterItemData.getEntryEnchanted(getOpenedStack(), slot);
    }

    public void setEntryEnchanted(Player player, int slot, @Nullable Boolean value) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryEnchanted(getOpenedStack(), slot, value);
        broadcastChanges();
    }

    public String getEntrySlotMappingExpression(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return "";
        return FilterItemData.getEntrySlotMappingExpression(getOpenedStack(), slot);
    }

    public void setEntrySlotMapping(Player player, int slot, String expression) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        java.util.BitSet parsed = SlotExpressionUtil.parseSlots(expression);
        FilterItemData.setEntrySlotMapping(getOpenedStack(), slot,
                parsed != null ? SlotExpressionUtil.bitSetToList(parsed).stream().mapToInt(Integer::intValue).toArray() : null);
        broadcastChanges();
    }

    public void clearFilterEntryItem(Player player, int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.clearEntryItem(getOpenedStack(), slot);
        isFluidSlot[slot] = false;
        isChemicalSlot[slot] = false;
        isTagSlot[slot] = false;
        ignoreUpdates = true;
        filterInventory.setItem(slot, ItemStack.EMPTY);
        ignoreUpdates = false;
        broadcastChanges();
    }

    // Per-slot tag
    public String getEntryTag(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return null;
        return FilterItemData.getEntryTag(getOpenedStack(), slot);
    }

    public boolean isTagSlot(int slot) {
        return slot >= 0 && slot < slotCount && isTagSlot[slot];
    }

    private boolean isSlotOccupied(int slot) {
        return isFluidSlot[slot] || isChemicalSlot[slot] || isTagSlot[slot]
                || !filterInventory.getItem(slot).isEmpty()
                || FilterItemData.isNbtOnlySlot(getOpenedStack(), slot);
    }

    public void setEntryTag(Player player, int slot, String tag) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        String normalizedTag = FilterTagUtil.normalizeTag(tag);
        if (normalizedTag == null) {
            clearEntryTag(slot);
            return;
        }
        FilterItemData.setEntryTag(getOpenedStack(), slot, normalizedTag);
        isTagSlot[slot] = true;
        isFluidSlot[slot] = false;
        isChemicalSlot[slot] = false;
        ignoreUpdates = true;
        filterInventory.setItem(slot, ItemStack.EMPTY);
        ignoreUpdates = false;
        broadcastChanges();
    }

    public void clearEntryTag(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryTag(getOpenedStack(), slot, null);
        isTagSlot[slot] = false;
        broadcastChanges();
    }

    // Per-slot NBT
    public String getEntryNbtPath(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return null;
        return FilterItemData.getEntryNbtPath(getOpenedStack(), slot);
    }

    public String getEntryNbtOperator(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return null;
        return FilterItemData.getEntryNbtOperator(getOpenedStack(), slot);
    }

    public List<FilterItemData.SlotNbtRule> getSlotNbtRules(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return List.of();
        return FilterItemData.getSlotNbtRules(getOpenedStack(), slot);
    }

    public boolean isSlotNbtMatchAny(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return false;
        return FilterItemData.isSlotNbtMatchAny(getOpenedStack(), slot);
    }

    public void addSlotNbtRule(Player player, int slot, String path, String operator) {
        addSlotNbtRule(player, slot, path, operator, "");
    }

    public void addSlotNbtRule(Player player, int slot, String path, String operator, String fallbackValue) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        ItemStack filterStack = getOpenedStack();
        Tag value = null;

        if (getTargetType() == FilterTargetType.FLUIDS) {
            FluidStack fluid = FilterItemData.getFluidEntry(filterStack, slot);
            if (!fluid.isEmpty()) {
                net.minecraft.nbt.CompoundTag fluidComponents = NbtFilterData.getSerializedComponents(
                        fluid, player.level().registryAccess());
                value = NbtFilterData.resolvePathValue(fluidComponents, path);
            }
        } else {
            ItemStack slotItem = FilterItemData.getEntry(filterStack, slot, player.level().registryAccess());
            if (!slotItem.isEmpty()) {
                value = NbtFilterData.resolvePathValue(slotItem, path, player.level().registryAccess());
            }
        }

        if (value == null)
            value = NbtFilterData.getDefaultValue(path);
        if (value == null && !fallbackValue.isEmpty())
            value = NbtFilterData.parseValueString(fallbackValue);
        if (value == null)
            return;
        FilterItemData.addSlotNbtRule(filterStack, slot, path, operator, value);
        broadcastChanges();
    }

    public void removeSlotNbtRule(int slot, int ruleIndex) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.removeSlotNbtRule(getOpenedStack(), slot, ruleIndex);
        broadcastChanges();
    }

    public void toggleSlotNbtMatchMode(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.toggleSlotNbtMatchMode(getOpenedStack(), slot);
        broadcastChanges();
    }

    public void setSlotNbtRuleValue(int slot, int ruleIndex, String valueStr) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        net.minecraft.nbt.Tag parsed = parseNbtValue(valueStr);
        if (parsed == null)
            return;
        FilterItemData.setSlotNbtRuleValue(getOpenedStack(), slot, ruleIndex, parsed);
        broadcastChanges();
    }

    private net.minecraft.nbt.Tag parseNbtValue(String input) {
        if (input == null || input.isEmpty())
            return null;
        try {
            return net.minecraft.nbt.TagParser.parseCompoundFully("{v:" + input + "}").get("v");
        } catch (Exception e) {
            return null;
        }
    }

    public void clearSlotNbtRules(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.clearSlotNbtRules(getOpenedStack(), slot);
        broadcastChanges();
    }

    public String getEntryDurabilityOp(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return null;
        return FilterItemData.getEntryDurabilityOp(getOpenedStack(), slot);
    }

    public int getEntryDurabilityValue(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return 0;
        return FilterItemData.getEntryDurabilityValue(getOpenedStack(), slot);
    }

    public void setEntryNbt(Player player, int slot, String path, String operator) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        ItemStack filterStack = getOpenedStack();
        ItemStack slotItem = FilterItemData.getEntry(filterStack, slot, player.level().registryAccess());
        if (slotItem.isEmpty())
            return;
        Tag value = NbtFilterData.resolvePathValue(slotItem, path, player.level().registryAccess());
        if (value == null)
            return;
        FilterItemData.setEntryNbt(filterStack, slot, path, value, operator);
        broadcastChanges();
    }

    public void setEntryNbtRaw(Player player, int slot, String path, String rawValue) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryNbtRaw(getOpenedStack(), slot, rawValue);
        broadcastChanges();
    }

    public void clearEntryNbt(Player player, int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryNbt(getOpenedStack(), slot, null, null);
        FilterItemData.setEntryNbtRaw(getOpenedStack(), slot, null);
        FilterItemData.clearSlotNbtRules(getOpenedStack(), slot);
        broadcastChanges();
    }

    // Per-slot durability
    public void setEntryDurability(Player player, int slot, String op, int value) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryDurability(getOpenedStack(), slot, op, value);
        broadcastChanges();
    }

    public void clearEntryDurability(Player player, int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;
        FilterItemData.setEntryDurability(getOpenedStack(), slot, null, 0);
        broadcastChanges();
    }

    public void setAmountValue(Player player, int amount) {
    }

    public void setDurabilityValue(Player player, int value) {
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
        if (nodeSource != null) {
            ChannelData ch = nodeSource.getChannel(nodeChannel);
            return ch != null ? ch.getFilterItem(nodeFilterSlot) : ItemStack.EMPTY;
        }
        if (inventorySlotIndex >= 0)
            return player.getInventory().getItem(inventorySlotIndex);
        if (hand == InteractionHand.OFF_HAND)
            return player.getOffhandItem();
        return (lockedSlot >= 0) ? player.getInventory().getItem(lockedSlot) : player.getMainHandItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide())
            return false;

        if (id == ID_TOGGLE_MODE)
            return toggleBlacklist();
        if (id == ID_CYCLE_TARGET)
            return cycleTargetType();
        if (id == ID_CYCLE_NAME_SCOPE && isNameMode)
            return cycleNameMatchScope();

        return false;
    }

    private boolean toggleBlacklist() {
        boolean newState = data.get(0) == 0;
        data.set(0, newState ? 1 : 0);

        ItemStack stack = getOpenedStack();
        if (isModMode)
            ModFilterData.setBlacklist(stack, newState);
        else if (isNameMode)
            NameFilterData.setBlacklist(stack, newState);
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
        if (isModMode)
            ModFilterData.setTargetType(stack, next);
        else if (isNameMode)
            NameFilterData.setTargetType(stack, next);
        else if (!isSlotMode)
            FilterItemData.setTargetType(stack, next);

        broadcastChanges();
        return true;
    }

    private boolean cycleNameMatchScope() {
        ItemStack stack = getOpenedStack();
        NameMatchScope next = NameFilterData.getMatchScope(stack).next();
        NameFilterData.setMatchScope(stack, next);
        data.set(2, next.ordinal());
        broadcastChanges();
        return true;
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

    public boolean setFluidFilterEntry(Player player, int slot, FluidStack fluid) {
        if (isSpecialMode || slot < 0 || slot >= slotCount || fluid.isEmpty())
            return false;
        if (hasFluid(fluid))
            return false;

        updateFilter(slot, s -> {
            FilterItemData.setFluidEntry(getOpenedStack(), s, fluid);
            isFluidSlot[s] = true;
            isChemicalSlot[s] = false;
            ignoreUpdates = true;
            filterInventory.setItem(s, ItemStack.EMPTY);
            ignoreUpdates = false;
        });
        return true;
    }

    public boolean setChemicalFilterEntry(Player player, int slot, String chemicalId) {
        if (isSpecialMode || slot < 0 || slot >= slotCount
                || chemicalId == null || chemicalId.isEmpty())
            return false;
        if (hasChemical(chemicalId))
            return false;

        updateFilter(slot, s -> {
            FilterItemData.setChemicalEntry(getOpenedStack(), s, chemicalId);
            isChemicalSlot[s] = true;
            isFluidSlot[s] = false;
            ignoreUpdates = true;
            filterInventory.setItem(s, ItemStack.EMPTY);
            ignoreUpdates = false;
        });
        return true;
    }

    public boolean setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (isSpecialMode || slot < 0 || slot >= slotCount || stack.isEmpty() || stack.is(ModTags.FILTERS))
            return false;
        if (hasItem(stack))
            return false;

        ItemStack itemEntry = stack.copyWithCount(1);
        updateFilter(slot, s -> {
            FilterItemData.setEntry(getOpenedStack(), s, itemEntry, player.level().registryAccess());
            isFluidSlot[s] = false;
            isChemicalSlot[s] = false;
            filterInventory.setItem(s, itemEntry);
        });
        return true;
    }

    private boolean hasFluid(FluidStack target) {
        for (int i = 0; i < slotCount; i++) {
            FluidStack existing = FilterItemData.getFluidEntry(getOpenedStack(), i);
            if (!existing.isEmpty() && existing.getFluid() == target.getFluid())
                return true;
        }
        return false;
    }

    private boolean hasItem(ItemStack target) {
        for (int i = 0; i < slotCount; i++) {
            if (isFluidSlot[i] || isChemicalSlot[i])
                continue;
            ItemStack existing = filterInventory.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, target))
                return true;
        }
        return false;
    }

    private boolean hasChemical(String chemicalId) {
        for (int i = 0; i < slotCount; i++) {
            String existing = FilterItemData.getChemicalEntry(getOpenedStack(), i);
            if (existing != null && existing.equals(chemicalId))
                return true;
        }
        return false;
    }

    public void clearFilterEntry(int slot) {
        if (isSpecialMode || slot < 0 || slot >= slotCount)
            return;

        updateFilter(slot, s -> {
            ItemStack stack = getOpenedStack();
            FilterItemData.setEntry(stack, s, ItemStack.EMPTY, player.level().registryAccess());
            FilterItemData.setFluidEntry(stack, s, FluidStack.EMPTY);
            FilterItemData.setChemicalEntry(stack, s, null);
            FilterItemData.setEntryTag(stack, s, null);
            FilterItemData.setEntryNbt(stack, s, null, null);
            FilterItemData.clearSlotNbtRules(stack, s);
            FilterItemData.setEntryDurability(stack, s, null, 0);
            FilterItemData.setEntrySlotMapping(stack, s, null);
            FilterItemData.setEntryEnchanted(stack, s, null);
            FilterItemData.setEntryBatch(stack, s, 0);
            FilterItemData.setEntryStock(stack, s, 0);
            isFluidSlot[s] = false;
            isChemicalSlot[s] = false;
            isTagSlot[s] = false;
            filterInventory.setItem(s, ItemStack.EMPTY);
        });
    }

    private void updateFilter(int slot, java.util.function.IntConsumer action) {
        action.accept(slot);
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ContainerInput clickType, Player player) {
        if (clickType == ContainerInput.PICKUP && slotId >= 0 && slotId < slots.size()) {

            if (!isSpecialMode && slotId < slotCount) {
                handleGhostGridClick(player, slotId, dragType);
                return;
            }
            if ((isTagMode || isModMode) && slotId == getExtractorSlotIndex()) {
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

        if (getTargetType() == FilterTargetType.FLUIDS) {
            FluidStack fluid = getFluidFromItem(held);
            if (!fluid.isEmpty()) {
                setFluidFilterEntry(player, slotId, fluid);
                return;
            }
        }

        if (getTargetType() == FilterTargetType.CHEMICALS) {
            String chemId = MekanismCompat.getChemicalIdFromItem(held);
            if (chemId != null) {
                setChemicalFilterEntry(player, slotId, chemId);
                return;
            }
        }

        if (interactionMode == 1) {
            FluidStack fluid = getFluidFromItem(held);
            if (!fluid.isEmpty()) {
                setFluidFilterEntry(player, slotId, fluid);
                return;
            }
            String chemId = MekanismCompat.getChemicalIdFromItem(held);
            if (chemId != null) {
                setChemicalFilterEntry(player, slotId, chemId);
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
                extractorInventory.setItem(0, new ItemStack(fluid.getFluid().getBucket()));
                broadcastChanges();
                return;
            }
            String chemId = MekanismCompat.getChemicalIdFromItem(held);
            if (chemId != null) {
                extractorInventory.setItem(0, held.copyWithCount(1));
                broadcastChanges();
                return;
            }
        }

        FluidStack fluid = getFluidFromItem(held);
        if (!fluid.isEmpty()) {
            extractorInventory.setItem(0, new ItemStack(fluid.getFluid().getBucket()));
            broadcastChanges();
            return;
        }

        String chemId = MekanismCompat.getChemicalIdFromItem(held);
        if (chemId != null) {
            extractorInventory.setItem(0, held.copyWithCount(1));
            broadcastChanges();
            return;
        }

        extractorInventory.setItem(0, held.copyWithCount(1));
        broadcastChanges();
    }

    private FluidStack getFluidFromItem(ItemStack stack) {
        FluidStack contained = FluidUtil.getFirstStackContained(stack);
        if (!contained.isEmpty()) {
            return contained;
        }

        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid.getBucket() == stack.getItem()) {
                return new FluidStack(fluid, 1000);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isSpecialMode)
            return ItemStack.EMPTY;

        Slot source = slots.get(index);
        if (!source.hasItem())
            return ItemStack.EMPTY;

        if (isPlayerInventorySlot(index)) {
            ItemStack held = source.getItem();

            if (getTargetType() == FilterTargetType.FLUIDS) {
                FluidStack fluid = getFluidFromItem(held);
                if (!fluid.isEmpty()) {
                    for (int i = 0; i < slotCount; i++) {
                        if (isSlotOccupied(i))
                            continue;
                        FluidStack existing = FilterItemData.getFluidEntry(getOpenedStack(), i);
                        if (existing.isEmpty()) {
                            if (setFluidFilterEntry(player, i, fluid))
                                break;
                        }
                    }
                    return ItemStack.EMPTY;
                }
            }

            if (getTargetType() == FilterTargetType.CHEMICALS) {
                String chemId = MekanismCompat.getChemicalIdFromItem(held);
                if (chemId != null) {
                    for (int i = 0; i < slotCount; i++) {
                        if (isSlotOccupied(i))
                            continue;
                        String existing = FilterItemData.getChemicalEntry(getOpenedStack(), i);
                        if (existing == null) {
                            if (setChemicalFilterEntry(player, i, chemId))
                                break;
                        }
                    }
                    return ItemStack.EMPTY;
                }
            }

            if (!held.is(ModTags.FILTERS)) {
                for (int i = 0; i < slotCount; i++) {
                    if (isSlotOccupied(i))
                        continue;
                    if (setItemFilterEntry(player, i, held))
                        break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (nodeSource != null)
            return nodeSource.isAlive();
        ItemStack stack = getOpenedStack();
        return !stack.isEmpty() && (stack.getItem() instanceof BaseFilterItem ||
                stack.getItem() instanceof ModFilterItem ||
                stack.getItem() instanceof SlotFilterItem ||
                stack.getItem() instanceof NameFilterItem);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && !isSpecialMode) {
            saveFilterItems(getOpenedStack(), player.level().registryAccess());
        }
        if (!player.level().isClientSide() && nodeSource != null) {
            ServerPayloadHandler.propagateToLabelGroup(nodeSource, nodeChannel);
            ServerPayloadHandler.markNetworkDirty(nodeSource);
        }
    }

    private void loadFilterItems(ItemStack stack, HolderLookup.Provider provider) {
        for (int i = 0; i < slotCount; i++) {
            String tag = FilterItemData.getEntryTag(stack, i);
            FluidStack fluid = FilterItemData.getFluidEntry(stack, i);
            String chemical = FilterItemData.getChemicalEntry(stack, i);
            if (tag != null) {
                isTagSlot[i] = true;
                isFluidSlot[i] = false;
                isChemicalSlot[i] = false;
                filterInventory.setItem(i, ItemStack.EMPTY);
            } else if (!fluid.isEmpty()) {
                isTagSlot[i] = false;
                isFluidSlot[i] = true;
                isChemicalSlot[i] = false;
                filterInventory.setItem(i, ItemStack.EMPTY);
            } else if (chemical != null) {
                isTagSlot[i] = false;
                isFluidSlot[i] = false;
                isChemicalSlot[i] = true;
                filterInventory.setItem(i, ItemStack.EMPTY);
            } else {
                isTagSlot[i] = false;
                isFluidSlot[i] = false;
                isChemicalSlot[i] = false;
                filterInventory.setItem(i, FilterItemData.getEntry(stack, i, provider));
            }
        }
    }

    private void saveFilterItems(ItemStack stack, HolderLookup.Provider provider) {
        FilterItemData.setBlacklist(stack, isBlacklistMode());
        for (int i = 0; i < slotCount; i++) {
            if (isTagSlot[i] || isChemicalSlot[i]) {
                // data lives in NBT already
            } else if (FilterItemData.isNbtOnlySlot(stack, i)) {
                // nbt-only entries preserved in-place
            } else if (isFluidSlot[i]) {
                FluidStack fluid = FilterItemData.getFluidEntry(stack, i);
                FilterItemData.setFluidEntry(stack, i, fluid);
            } else {
                FilterItemData.setEntry(stack, i, filterInventory.getItem(i), provider);
            }
        }
    }

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
                isFluidSlot[getSlotIndex()] = false;
                isChemicalSlot[getSlotIndex()] = false;
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
