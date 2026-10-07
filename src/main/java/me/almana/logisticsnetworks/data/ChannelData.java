package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.component.ClipboardSnapshot.ChannelState;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.ChannelTelemetry;
import me.almana.logisticsnetworks.logic.ItemResourceOrder;
import me.almana.logisticsnetworks.logic.FluidResourceOrder;
import me.almana.logisticsnetworks.logic.PriorityRobin;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ChannelData {

    public static final int FILTER_SIZE = 6;

    private static final MapCodec<ChannelState> LEGACY_SETTINGS = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("Enabled", false).forGetter(ChannelState::enabled),
            ChannelMode.CODEC.lenientOptionalFieldOf("Mode", ChannelMode.IMPORT).forGetter(ChannelState::mode),
            ChannelType.CODEC.lenientOptionalFieldOf("Type", ChannelType.ITEM).forGetter(ChannelState::type),
            Codec.INT.lenientOptionalFieldOf("BatchSize", 8).forGetter(ChannelState::batchSize),
            Codec.INT.lenientOptionalFieldOf("TickDelay", 20).forGetter(ChannelState::tickDelay),
            ChannelState.DIRECTION.lenientOptionalFieldOf("IoDirection", Optional.of(Direction.UP))
                    .forGetter(ChannelState::direction),
            Codec.STRING.lenientOptionalFieldOf("RedstoneMode", "").forGetter(state -> state.redstoneMode().name()),
            DistributionMode.CODEC.lenientOptionalFieldOf("DistributionMode", DistributionMode.PRIORITY)
                    .forGetter(ChannelState::distributionMode),
            FilterMode.CODEC.lenientOptionalFieldOf("FilterMode", FilterMode.MATCH_ANY)
                    .forGetter(ChannelState::filterMode),
            Codec.INT.lenientOptionalFieldOf("Priority", 0).forGetter(ChannelState::priority),
            Codec.STRING.lenientOptionalFieldOf("Name", "").forGetter(ChannelState::name),
            Codec.BOOL.lenientOptionalFieldOf("ResourceRoundRobin", false)
                    .forGetter(ChannelState::resourceRoundRobin)
    ).apply(instance, ChannelState::fromSerialized));

    static final Codec<ChannelData> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ChannelState.MAP_CODEC.forGetter(ChannelData::settings),
            SlotStack.LIST_CODEC.lenientOptionalFieldOf("filters", List.of()).forGetter(ChannelData::filterSlots)
    ).apply(instance, ChannelData::of));
    static final Codec<ChannelData> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LEGACY_SETTINGS.forGetter(ChannelData::settings),
            SlotStack.LEGACY_LIST_CODEC.lenientOptionalFieldOf("Filters")
                    .forGetter(channel -> Optional.of(channel.filterSlots())),
            ComponentCodecs.STACK.lenientOptionalFieldOf("FilterItem", ItemStack.EMPTY)
                    .forGetter(channel -> ItemStack.EMPTY)
    ).apply(instance, (settings, filters, filterItem) ->
            of(settings, filters.orElseGet(() -> List.of(new SlotStack(0, filterItem))))));
    public static final Codec<ChannelData> CODEC = Codec.withAlternative(CURRENT_CODEC, LEGACY_CODEC);
    public static final Codec<List<ChannelData>> LIST_CODEC =
            ComponentCodecs.lenient(CODEC, ChannelData::new).listOf();
    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private boolean enabled;
    private ChannelMode mode = ChannelMode.IMPORT;
    private ChannelType type = ChannelType.ITEM;
    private int batchSize = 8;
    private int tickDelay = 20;
    @Nullable
    private Direction ioDirection = Direction.UP;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private DistributionMode distributionMode = DistributionMode.PRIORITY;
    private FilterMode filterMode = FilterMode.MATCH_ANY;
    private int priority = 0;
    private String name = "";
    private boolean resourceRoundRobin;
    private transient ItemResourceOrder.Cursor itemResourceCursor;
    private transient FluidResourceOrder.Cursor fluidResourceCursor;
    private transient PriorityRobin.Cursor robinCursor;

    private final ItemStack[] filterItems = new ItemStack[FILTER_SIZE];
    private final transient ChannelTelemetry telemetry = new ChannelTelemetry();
    private final transient FilterItemData.ReadCache readCache = FilterItemData.createReadCache();

    public ChannelData() {
        this(false);
    }

    public ChannelData(boolean enabled) {
        this.enabled = enabled;
        Arrays.fill(filterItems, ItemStack.EMPTY);
    }

    private static ChannelData of(ChannelState settings, List<SlotStack> filters) {
        ChannelData channel = new ChannelData(settings.enabled());
        channel.setMode(settings.mode());
        channel.setType(settings.type());
        channel.setBatchSize(settings.batchSize());
        channel.setTickDelay(settings.tickDelay());
        channel.setIoDirection(settings.direction().orElse(null));
        channel.setRedstoneMode(settings.redstoneMode());
        channel.setDistributionMode(settings.distributionMode());
        channel.setFilterMode(settings.filterMode());
        channel.setPriority(settings.priority());
        channel.setName(settings.name());
        channel.resourceRoundRobin = settings.resourceRoundRobin();
        channel.placeFilters(filters);
        return channel;
    }

    private ChannelState settings() {
        return new ChannelState(enabled, mode, type, batchSize, tickDelay, Optional.ofNullable(ioDirection),
                redstoneMode, distributionMode, filterMode, priority, name, resourceRoundRobin);
    }

    private List<SlotStack> filterSlots() {
        return SlotStack.nonEmpty(Arrays.asList(filterItems));
    }

    private void placeFilters(List<SlotStack> filters) {
        List<ItemStack> overflow = new ArrayList<>();
        for (SlotStack filter : filters) {
            if (filter.slot() < 0 || filter.stack().isEmpty()) {
                continue;
            }
            if (filter.slot() < FILTER_SIZE) {
                filterItems[filter.slot()] = filter.stack();
            } else {
                overflow.add(filter.stack());
            }
        }
        placeOverflowFilters(overflow);
    }

    // Filter Upper Fixer: relocate legacy slots >= FILTER_SIZE into free slots
    private void placeOverflowFilters(List<ItemStack> overflow) {
        if (overflow.isEmpty()) {
            return;
        }
        int next = 0;
        for (ItemStack stack : overflow) {
            while (next < FILTER_SIZE && !filterItems[next].isEmpty()) {
                next++;
            }
            if (next >= FILTER_SIZE) {
                break;
            }
            filterItems[next] = stack;
            next++;
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
        if (type != null) {
            this.type = type;
            if (type == ChannelType.ENERGY)
                tickDelay = 1;
        }
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
        this.tickDelay = type == ChannelType.ENERGY ? 1 : Math.max(1, tickDelay);
    }

    @Nullable
    public Direction getIoDirection() {
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

    public FilterItemData.ReadCache getReadCache() {
        return readCache;
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
        resourceRoundRobin = source.resourceRoundRobin;
        resetResourceRotation();
        robinCursor = null;
        this.enabled = source.enabled;
        this.mode = source.mode;
        setType(source.type);
        this.batchSize = source.batchSize;
        setTickDelay(source.tickDelay);
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
        if (!settings().equals(other.settings())) {
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
