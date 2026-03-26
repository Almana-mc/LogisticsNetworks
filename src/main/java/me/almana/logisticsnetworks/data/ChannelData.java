package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.ChannelTelemetry;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
    private final transient ChannelTelemetry telemetry = new ChannelTelemetry();

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
                    entry.store("Item", ItemStack.OPTIONAL_CODEC, filterItems[i]);
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
            enabled = tag.getBooleanOr(KEY_ENABLED, enabled);

        mode = getEnum(tag, KEY_MODE, ChannelMode.class, ChannelMode.IMPORT);
        type = getEnum(tag, KEY_TYPE, ChannelType.class, ChannelType.ITEM);
        redstoneMode = getEnum(tag, KEY_REDSTONE, RedstoneMode.class, RedstoneMode.ALWAYS_ON);
        distributionMode = getEnum(tag, KEY_DISTRIB, DistributionMode.class, DistributionMode.PRIORITY);
        filterMode = getEnum(tag, KEY_FILTER_MODE, FilterMode.class, FilterMode.MATCH_ANY);

        if (tag.contains(KEY_BATCH))
            batchSize = Math.max(1, tag.getIntOr(KEY_BATCH, batchSize));
        if (tag.contains(KEY_DELAY))
            tickDelay = Math.max(1, tag.getIntOr(KEY_DELAY, tickDelay));

        if (tag.contains(KEY_IO)) {
            ioDirection = Direction.byName(tag.getStringOr(KEY_IO, ioDirection.getName()));
            if (ioDirection == null)
                ioDirection = Direction.UP;
        }

        if (tag.contains(KEY_PRIORITY)) {
            priority = Math.max(-99, Math.min(99, tag.getIntOr(KEY_PRIORITY, priority)));
        }

        Arrays.fill(filterItems, ItemStack.EMPTY);
        if (provider != null && tag.contains(KEY_FILTERS)) {
            ListTag list = tag.getListOrEmpty(KEY_FILTERS);
            for (Tag t : list) {
                if (t instanceof CompoundTag ct) {
                    int slot = ct.getIntOr("Slot", -1);
                    if (slot >= 0 && slot < FILTER_SIZE) {
                        filterItems[slot] = ct.read("Item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
                    }
                }
            }
        } else if (provider != null && tag.contains("FilterItem")) {
            filterItems[0] = tag.read("FilterItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
    }

    public void save(ValueOutput tag) {
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

        var list = tag.childrenList(KEY_FILTERS);
        for (int i = 0; i < FILTER_SIZE; i++) {
            if (filterItems[i].isEmpty()) {
                continue;
            }
            var entry = list.addChild();
            entry.putInt("Slot", i);
            entry.store("Item", ItemStack.OPTIONAL_CODEC, filterItems[i]);
        }
    }

    public void load(ValueInput tag) {
        enabled = tag.getBooleanOr(KEY_ENABLED, enabled);
        mode = parseEnum(tag.getStringOr(KEY_MODE, mode.name()), ChannelMode.class, ChannelMode.IMPORT);
        type = parseEnum(tag.getStringOr(KEY_TYPE, type.name()), ChannelType.class, ChannelType.ITEM);
        redstoneMode = parseEnum(tag.getStringOr(KEY_REDSTONE, redstoneMode.name()), RedstoneMode.class, RedstoneMode.ALWAYS_ON);
        distributionMode = parseEnum(tag.getStringOr(KEY_DISTRIB, distributionMode.name()), DistributionMode.class,
                DistributionMode.PRIORITY);
        filterMode = parseEnum(tag.getStringOr(KEY_FILTER_MODE, filterMode.name()), FilterMode.class, FilterMode.MATCH_ANY);
        batchSize = Math.max(1, tag.getIntOr(KEY_BATCH, batchSize));
        tickDelay = Math.max(1, tag.getIntOr(KEY_DELAY, tickDelay));

        Direction parsedDirection = Direction.byName(tag.getStringOr(KEY_IO, ioDirection.getName()));
        ioDirection = parsedDirection == null ? Direction.UP : parsedDirection;
        priority = Math.max(-99, Math.min(99, tag.getIntOr(KEY_PRIORITY, priority)));

        Arrays.fill(filterItems, ItemStack.EMPTY);
        for (ValueInput entry : tag.childrenListOrEmpty(KEY_FILTERS)) {
            int slot = entry.getIntOr("Slot", -1);
            if (slot < 0 || slot >= FILTER_SIZE) {
                continue;
            }
            filterItems[slot] = entry.read("Item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
        if (filterItems[0].isEmpty()) {
            filterItems[0] = tag.read("FilterItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
    }

    private <E extends Enum<E>> E getEnum(CompoundTag tag, String key, Class<E> enumClass, E defaultValue) {
        if (tag.contains(key)) {
            try {
                return Enum.valueOf(enumClass, tag.getStringOr(key, defaultValue.name()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defaultValue;
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass, E defaultValue) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
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
            filterItems[slot] = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public void copyFrom(ChannelData source) {
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
        for (int i = 0; i < FILTER_SIZE; i++) {
            this.filterItems[i] = source.filterItems[i].isEmpty() ? ItemStack.EMPTY : source.filterItems[i].copy();
        }
    }
}
