package me.almana.logisticsnetworks.data;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ChannelData {

    public static final int FILTER_SIZE = 9;
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

    private boolean enabled;
    private ChannelMode mode = ChannelMode.IMPORT;
    private ChannelType type = ChannelType.ITEM;
    private int batchSize = 8;
    private int tickDelay = 20;
    private Direction ioDirection = Direction.UP;
    private RedstoneMode redstoneMode = RedstoneMode.ALWAYS_ON;
    private DistributionMode distributionMode = DistributionMode.PRIORITY;
    private FilterMode filterMode = FilterMode.MATCH_ANY;
    private int priority = 0;

    private final ItemStack[] filterItems = new ItemStack[FILTER_SIZE];

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
        tag.putString(KEY_IO, ioDirection.getName());
        tag.putString(KEY_REDSTONE, redstoneMode.name());
        tag.putString(KEY_DISTRIB, distributionMode.name());
        tag.putString(KEY_FILTER_MODE, filterMode.name());
        tag.putInt(KEY_PRIORITY, priority);

        if (provider != null) {
            ListTag list = new ListTag();
            for (int i = 0; i < FILTER_SIZE; i++) {
                if (!filterItems[i].isEmpty()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("Slot", i);
                    entry.put("Item", filterItems[i].save(provider));
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
        redstoneMode = getEnum(tag, KEY_REDSTONE, RedstoneMode.class, RedstoneMode.ALWAYS_ON);
        distributionMode = getEnum(tag, KEY_DISTRIB, DistributionMode.class, DistributionMode.PRIORITY);
        filterMode = getEnum(tag, KEY_FILTER_MODE, FilterMode.class, FilterMode.MATCH_ANY);

        if (tag.contains(KEY_BATCH))
            batchSize = Math.max(1, tag.getInt(KEY_BATCH));
        if (tag.contains(KEY_DELAY))
            tickDelay = Math.max(1, tag.getInt(KEY_DELAY));

        if (tag.contains(KEY_IO)) {
            ioDirection = Direction.byName(tag.getString(KEY_IO));
            if (ioDirection == null)
                ioDirection = Direction.UP;
        }

        if (tag.contains(KEY_PRIORITY)) {
            priority = Math.max(-99, Math.min(99, tag.getInt(KEY_PRIORITY)));
        }

        Arrays.fill(filterItems, ItemStack.EMPTY);
        if (provider != null && tag.contains(KEY_FILTERS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_FILTERS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag ct) {
                    int slot = ct.getInt("Slot");
                    if (slot >= 0 && slot < FILTER_SIZE) {
                        filterItems[slot] = ItemStack.parseOptional(provider, ct.getCompound("Item"));
                    }
                }
            }
        } else if (provider != null && tag.contains("FilterItem", Tag.TAG_COMPOUND)) {
            filterItems[0] = ItemStack.parseOptional(provider, tag.getCompound("FilterItem"));
        }
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

    public Direction getIoDirection() {
        return ioDirection;
    }

    public void setIoDirection(Direction ioDirection) {
        if (ioDirection != null)
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

    public void setPriority(int priority) {
        this.priority = Math.max(-99, Math.min(99, priority));
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
            filterItems[slot] = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }
}
