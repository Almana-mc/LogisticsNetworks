package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.ChannelTelemetry;
import me.almana.logisticsnetworks.logic.FluidResourceOrder;
import me.almana.logisticsnetworks.logic.ItemResourceOrder;
import me.almana.logisticsnetworks.logic.PriorityRobin;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.util.ItemStackCompat;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChannelData {

    public static final int FILTER_SIZE = 6;
    private static final String KEY_ENABLED = "Enabled";
    private static final String KEY_MODE = "Mode";
    private static final String KEY_TYPE = "Type";
    private static final String KEY_BATCH = "BatchSize";
    private static final String KEY_DELAY = "TickDelay";
    private static final String KEY_IO = "IoDirection";
    private static final String KEY_REDSTONE = "RedstoneMode";
    private static final String KEY_DISTRIB = "DistributionMode";
    private static final String KEY_FILTER_MODE = "FilterMode";
    private static final String KEY_PRIORITY = "Priority";
    private static final String KEY_FILTERS = "Filters";
    private static final String KEY_NAME = "Name";
    private static final String KEY_RESOURCE_ROUND_ROBIN = "ResourceRoundRobin";

    private boolean enabled;
    private ChannelMode mode = ChannelMode.IMPORT;
    private ChannelType type = ChannelType.ITEM;
    private int batchSize = 8;
    private int tickDelay = 20;
    private @Nullable Direction ioDirection = Direction.UP;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private DistributionMode distributionMode = DistributionMode.PRIORITY;
    private FilterMode filterMode = FilterMode.MATCH_ANY;
    private int priority = 0;
    private String name = "";
    private boolean resourceRoundRobin;
    private transient ItemResourceOrder.Cursor itemResourceCursor;
    private transient FluidResourceOrder.Cursor fluidResourceCursor;

    private final transient ChannelTelemetry telemetry = new ChannelTelemetry();
    private transient PriorityRobin.Cursor robinCursor;
    private final ItemStack[] filterItems = new ItemStack[FILTER_SIZE];
    private final transient List<ItemStack> ejectedFilters = new ArrayList<>();

    public ChannelData() {
        this(false);
    }

    public ChannelData(boolean enabled) {
        this.enabled = enabled;
        Arrays.fill(filterItems, ItemStack.EMPTY);
    }

    public CompoundTag save(@Nullable HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ENABLED, enabled);
        tag.putString(KEY_MODE, mode.name());
        tag.putString(KEY_TYPE, type.name());
        tag.putInt(KEY_BATCH, batchSize);
        tag.putInt(KEY_DELAY, tickDelay);
        tag.putString(KEY_IO, ioDirection != null ? ioDirection.getName() : "all");
        tag.putString(KEY_REDSTONE, redstoneMode.name());
        tag.putString(KEY_DISTRIB, distributionMode.name());
        tag.putString(KEY_FILTER_MODE, filterMode.name());
        tag.putInt(KEY_PRIORITY, priority);
        if (!name.isEmpty())
            tag.putString(KEY_NAME, name);
        if (resourceRoundRobin)
            tag.putBoolean(KEY_RESOURCE_ROUND_ROBIN, true);

        if (provider != null) {
            ListTag list = new ListTag();
            for (int i = 0; i < FILTER_SIZE; i++) {
                if (!filterItems[i].isEmpty()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("Slot", i);
                    entry.put("Item", ItemStackCompat.save(filterItems[i], provider));
                    list.add(entry);
                }
            }
            if (!list.isEmpty()) {
                tag.put(KEY_FILTERS, list);
            }
        }
        return tag;
    }

    public void load(CompoundTag tag, @Nullable HolderLookup.Provider provider) {
        if (tag.contains(KEY_ENABLED))
            enabled = tag.getBoolean(KEY_ENABLED);

        mode = getEnum(tag, KEY_MODE, ChannelMode.class, ChannelMode.IMPORT);
        type = getEnum(tag, KEY_TYPE, ChannelType.class, ChannelType.ITEM);
        String redstone = tag.getString(KEY_REDSTONE);
        redstoneMode = RedstoneMode.fromSerialized(redstone);
        if (RedstoneMode.disablesChannel(redstone))
            enabled = false;
        distributionMode = getEnum(tag, KEY_DISTRIB, DistributionMode.class, DistributionMode.PRIORITY);
        filterMode = getEnum(tag, KEY_FILTER_MODE, FilterMode.class, FilterMode.MATCH_ANY);

        if (tag.contains(KEY_BATCH))
            batchSize = Math.max(1, tag.getInt(KEY_BATCH));
        if (tag.contains(KEY_DELAY))
            tickDelay = Math.max(1, tag.getInt(KEY_DELAY));

        if (tag.contains(KEY_IO)) {
            String ioStr = tag.getString(KEY_IO);
            if ("all".equals(ioStr)) {
                ioDirection = null;
            } else {
                ioDirection = Direction.byName(ioStr);
                if (ioDirection == null)
                    ioDirection = Direction.UP;
            }
        }

        if (tag.contains(KEY_PRIORITY)) {
            priority = Math.max(-99, Math.min(99, tag.getInt(KEY_PRIORITY)));
        }

        name = tag.contains(KEY_NAME) ? tag.getString(KEY_NAME) : "";
        resourceRoundRobin = tag.getBoolean(KEY_RESOURCE_ROUND_ROBIN);
        resetResourceRotation();

        Arrays.fill(filterItems, ItemStack.EMPTY);
        ejectedFilters.clear();
        List<ItemStack> overflow = new ArrayList<>();
        if (provider != null && tag.contains(KEY_FILTERS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_FILTERS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag ct) {
                    placeFilter(ct.getInt("Slot"), ItemStackCompat.parseOptional(provider, ct.getCompound("Item")),
                            overflow);
                }
            }
        } else if (provider != null && tag.contains("FilterItem", Tag.TAG_COMPOUND)) {
            placeFilter(0, ItemStackCompat.parseOptional(provider, tag.getCompound("FilterItem")), overflow);
        }
        placeOverflowFilters(overflow);
    }

    private void placeFilter(int slot, ItemStack stack, List<ItemStack> overflow) {
        if (slot < 0 || stack.isEmpty())
            return;
        if (isRetiredFilter(stack)) {
            ejectedFilters.add(stack);
        } else if (slot < FILTER_SIZE) {
            filterItems[slot] = stack;
        } else {
            overflow.add(stack);
        }
    }

    // Relocate legacy overflow filters
    private void placeOverflowFilters(List<ItemStack> overflow) {
        int next = 0;
        for (ItemStack stack : overflow) {
            while (next < FILTER_SIZE && !filterItems[next].isEmpty())
                next++;
            if (next < FILTER_SIZE) {
                filterItems[next++] = stack;
            } else {
                ejectedFilters.add(stack);
            }
        }
    }

    private static boolean isRetiredFilter(ItemStack stack) {
        return stack.is(Registration.TAG_FILTER.get()) || stack.is(Registration.AMOUNT_FILTER.get())
                || stack.is(Registration.DURABILITY_FILTER.get()) || stack.is(Registration.NBT_FILTER.get())
                || stack.is(Registration.SLOT_FILTER.get());
    }

    public List<ItemStack> takeEjectedFilters() {
        List<ItemStack> taken = new ArrayList<>(ejectedFilters);
        ejectedFilters.clear();
        return taken;
    }

    private <E extends Enum<E>> E getEnum(CompoundTag tag, String key, Class<E> enumClass, E defaultValue) {
        if (tag.contains(key)) {
            try {
                return Enum.valueOf(enumClass, tag.getString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defaultValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ChannelMode getMode() {
        return mode;
    }

    public void setMode(ChannelMode mode) {
        if (mode != null)
            this.mode = mode;
    }

    public ChannelType getType() {
        return type;
    }

    public void setType(ChannelType type) {
        if (type != null)
            this.type = type;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public int getTickDelay() {
        return tickDelay;
    }

    public void setTickDelay(int tickDelay) {
        this.tickDelay = Math.max(1, tickDelay);
    }

    public @Nullable Direction getIoDirection() {
        return ioDirection;
    }

    public void setIoDirection(@Nullable Direction ioDirection) {
        this.ioDirection = ioDirection;
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode redstoneMode) {
        if (redstoneMode != null)
            this.redstoneMode = redstoneMode;
    }

    public DistributionMode getDistributionMode() {
        return distributionMode;
    }

    public void setDistributionMode(DistributionMode distributionMode) {
        if (distributionMode != null)
            this.distributionMode = distributionMode;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        if (filterMode != null)
            this.filterMode = filterMode;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isResourceRoundRobin() {
        return resourceRoundRobin;
    }

    public void setResourceRoundRobin(boolean value) {
        if (resourceRoundRobin != value) resetResourceRotation();
        resourceRoundRobin = value;
    }

    public boolean canRotateResources() {
        return resourceRoundRobin && mode == ChannelMode.EXPORT
                && (type == ChannelType.ITEM || type == ChannelType.FLUID)
                && Arrays.stream(filterItems).anyMatch(stack -> !stack.isEmpty());
    }

    @Nullable
    public ItemResourceOrder.Cursor getItemResourceCursor() {
        return itemResourceCursor;
    }

    public void setItemResourceCursor(ItemResourceOrder.Cursor cursor) {
        itemResourceCursor = cursor;
    }

    @Nullable
    public FluidResourceOrder.Cursor getFluidResourceCursor() {
        return fluidResourceCursor;
    }

    public void setFluidResourceCursor(FluidResourceOrder.Cursor cursor) {
        fluidResourceCursor = cursor;
    }

    public void resetResourceRotation() {
        itemResourceCursor = null;
        fluidResourceCursor = null;
    }

    @Nullable
    public PriorityRobin.Cursor getRobinCursor() {
        return robinCursor;
    }

    public void setRobinCursor(PriorityRobin.Cursor cursor) {
        robinCursor = cursor;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(-99, Math.min(99, priority));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public ChannelTelemetry getTelemetry() {
        return telemetry;
    }

    public ItemStack[] getFilterItems() {
        return filterItems;
    }

    public ItemStack getFilterItem(int slot) {
        if (slot >= 0 && slot < FILTER_SIZE)
            return filterItems[slot];
        return ItemStack.EMPTY;
    }

    public void setFilterItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SIZE) {
            filterItems[slot] = stack == null ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
        }
    }

    public void copyFrom(ChannelData source) {
        resourceRoundRobin = source.resourceRoundRobin;
        resetResourceRotation();
        robinCursor = null;
        this.enabled = source.enabled;
        this.mode = source.mode;
        this.type = source.type;
        this.batchSize = source.batchSize;
        this.tickDelay = source.tickDelay;
        this.ioDirection = source.ioDirection;
        this.redstoneMode = source.redstoneMode;
        this.distributionMode = source.distributionMode;
        this.filterMode = source.filterMode;
        this.priority = source.priority;
        this.name = source.name;
        for (int i = 0; i < FILTER_SIZE; i++) {
            this.filterItems[i] = source.filterItems[i].isEmpty() ? ItemStack.EMPTY : source.filterItems[i].copy();
        }
    }

    public boolean sameSettings(ChannelData other) {
        if (enabled != other.enabled || mode != other.mode || type != other.type
                || batchSize != other.batchSize || tickDelay != other.tickDelay
                || ioDirection != other.ioDirection || redstoneMode != other.redstoneMode
                || distributionMode != other.distributionMode || filterMode != other.filterMode
                || priority != other.priority || !name.equals(other.name)
                || resourceRoundRobin != other.resourceRoundRobin) {
            return false;
        }
        for (int slot = 0; slot < FILTER_SIZE; slot++) {
            if (!ItemStack.matches(filterItems[slot], other.filterItems[slot])) {
                return false;
            }
        }
        return true;
    }
}



