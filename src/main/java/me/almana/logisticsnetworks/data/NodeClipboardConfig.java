package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.component.ClipboardSnapshot;
import me.almana.logisticsnetworks.component.FilterComponentData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAction;
import me.almana.logisticsnetworks.integration.storage.StorageInventory;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NodeClipboardConfig {

    private static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_UPGRADES = "upgrades";
    private static final String KEY_REQUIRED_ITEMS = "required_items";
    private static final String KEY_NETWORK_ID = "network_id";
    private static final String KEY_NETWORK_NAME = "network_name";

    private static final String KEY_INDEX = "index";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MODE = "mode";
    private static final String KEY_TYPE = "type";
    private static final String KEY_BATCH = "batch";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_IO = "io";
    private static final String KEY_REDSTONE = "redstone";
    private static final String KEY_DISTRIBUTION = "distribution";
    private static final String KEY_FILTER_MODE = "filter_mode";
    private static final String KEY_PRIORITY = "priority";
    private static final String KEY_VISIBLE = "renderVisible";
    private static final String KEY_NODE_LABEL = "node_label";

    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_ITEM = "item";
    private static final String KEY_COUNT = "count";
    private static final String KEY_CH_NAME = "name";

    private final ChannelConfig[] channels;
    private final ItemStack[][] filterItems;
    private final ItemStack[] upgradeItems;
    @Nullable
    private UUID networkId;
    @Nullable
    private String networkName;
    private boolean renderVisible;
    private String nodeLabel = "";

    public enum PasteResult {
        SUCCESS,
        CLIPBOARD_INVALID,
        INCOMPATIBLE_TARGET,
        MISSING_ITEMS
    }

    private record Requirement(ItemStack stack, int count) {
    }

    public record RequiredItem(ItemStack stack, int count) {
    }

    public record PasteOutcome(PasteResult result, List<RequiredItem> missingItems) {
        public PasteOutcome(PasteResult result) {
            this(result, List.of());
        }

        public PasteOutcome {
            missingItems = List.copyOf(missingItems);
        }
    }

    private static final class ChannelConfig {
        boolean enabled;
        ChannelMode mode;
        ChannelType type;
        int batchSize;
        int tickDelay;
        @Nullable
        Direction ioDirection;
        RedstoneMode redstoneMode;
        DistributionMode distributionMode;
        FilterMode filterMode;
        int priority;
        String name = "";
        boolean resourceRoundRobin;
    }

    private NodeClipboardConfig(ChannelConfig[] channels, ItemStack[][] filterItems, ItemStack[] upgradeItems,
            @Nullable UUID networkId, @Nullable String networkName) {
        this.channels = channels;
        this.filterItems = filterItems;
        this.upgradeItems = upgradeItems;
        this.networkId = networkId;
        this.networkName = networkName;
        this.renderVisible = true;
    }

    public static NodeClipboardConfig createEmpty() {
        ChannelConfig[] channels = new ChannelConfig[LogisticsNodeEntity.CHANNEL_COUNT];
        ItemStack[][] filters = new ItemStack[LogisticsNodeEntity.CHANNEL_COUNT][ChannelData.FILTER_SIZE];
        ItemStack[] upgrades = new ItemStack[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];

        for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
            channels[channel] = defaultChannelConfig();
            Arrays.fill(filters[channel], ItemStack.EMPTY);
        }
        Arrays.fill(upgrades, ItemStack.EMPTY);
        return new NodeClipboardConfig(channels, filters, upgrades, null, null);
    }

    public ClipboardSnapshot toComponentSnapshot(@Nullable HolderLookup.Provider provider) {
        List<ClipboardSnapshot.ChannelState> channelStates = new ArrayList<>(channels.length);
        for (int channel = 0; channel < channels.length; channel++) {
            ChannelConfig config = getChannelConfig(channel);
            channelStates.add(new ClipboardSnapshot.ChannelState(
                    config.enabled,
                    config.mode,
                    config.type,
                    config.batchSize,
                    config.tickDelay,
                    Optional.ofNullable(config.ioDirection),
                    config.redstoneMode,
                    config.distributionMode,
                    config.filterMode,
                    config.priority,
                    config.name, config.resourceRoundRobin));
        }

        List<ClipboardSnapshot.ItemSlot> upgrades = new ArrayList<>();
        for (int slot = 0; slot < upgradeItems.length; slot++) {
            ItemStack stack = upgradeItems[slot];
            if (!stack.isEmpty()) {
                upgrades.add(new ClipboardSnapshot.ItemSlot(slot, StackSnapshot.of(stack.copyWithCount(1))));
            }
        }

        return new ClipboardSnapshot(
                channelStates,
                snapshotFilters(provider),
                upgrades,
                Optional.ofNullable(networkId),
                Optional.ofNullable(networkName),
                renderVisible,
                nodeLabel);
    }

    private List<ClipboardSnapshot.FilterSlot> snapshotFilters(@Nullable HolderLookup.Provider provider) {
        List<ClipboardSnapshot.FilterSlot> filters = new ArrayList<>();
        for (int channel = 0; channel < filterItems.length; channel++) {
            for (int slot = 0; slot < filterItems[channel].length; slot++) {
                ItemStack stack = filterItems[channel][slot];
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack copy = stack.copyWithCount(1);
                FilterComponentData.migrate(copy, provider);
                filters.add(new ClipboardSnapshot.FilterSlot(channel, slot, StackSnapshot.of(copy)));
            }
        }
        return filters;
    }

    public static NodeClipboardConfig fromComponentSnapshot(ClipboardSnapshot snapshot) {
        NodeClipboardConfig result = createEmpty();
        int channelCount = Math.min(result.channels.length, snapshot.channels().size());
        for (int channel = 0; channel < channelCount; channel++) {
            ClipboardSnapshot.ChannelState state = snapshot.channels().get(channel);
            ChannelConfig config = result.channels[channel];
            config.enabled = state.enabled();
            config.mode = state.mode();
            config.type = state.type();
            config.batchSize = state.batchSize();
            config.tickDelay = config.type == ChannelType.ENERGY ? 1 : state.tickDelay();
            config.ioDirection = state.direction().orElse(null);
            config.redstoneMode = state.redstoneMode();
            config.distributionMode = state.distributionMode();
            config.filterMode = state.filterMode();
            config.priority = state.priority();
            config.name = state.name();
            config.resourceRoundRobin = state.resourceRoundRobin();
        }
        for (ClipboardSnapshot.FilterSlot filter : snapshot.filters()) {
            if (filter.channel() >= 0 && filter.channel() < result.filterItems.length
                    && filter.slot() >= 0 && filter.slot() < ChannelData.FILTER_SIZE) {
                result.filterItems[filter.channel()][filter.slot()] = filter.stack().toStack().copyWithCount(1);
            }
        }
        for (ClipboardSnapshot.ItemSlot upgrade : snapshot.upgrades()) {
            if (upgrade.slot() >= 0 && upgrade.slot() < result.upgradeItems.length) {
                result.upgradeItems[upgrade.slot()] = upgrade.stack().toStack().copyWithCount(1);
            }
        }
        result.networkId = snapshot.networkId().orElse(null);
        result.networkName = snapshot.networkName().orElse(null);
        result.renderVisible = snapshot.renderVisible();
        result.nodeLabel = snapshot.nodeLabel();
        return result;
    }

    public int getChannelCount() {
        return channels.length;
    }

    public int getFilterSlotCount() {
        return ChannelData.FILTER_SIZE;
    }

    public int getUpgradeSlotCount() {
        return upgradeItems.length;
    }

    public void clear() {
        for (int channel = 0; channel < channels.length; channel++) {
            channels[channel] = defaultChannelConfig();
            if (channel < filterItems.length) {
                Arrays.fill(filterItems[channel], ItemStack.EMPTY);
            }
        }
        Arrays.fill(upgradeItems, ItemStack.EMPTY);
        networkId = null;
        networkName = null;
        renderVisible = true;
        nodeLabel = "";
    }

    @Nullable
    public UUID getNetworkId() {
        return networkId;
    }

    @Nullable
    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkTarget(@Nullable UUID id, @Nullable String name) {
        networkId = id;
        networkName = trim(name, 32);
        if (networkName.isEmpty()) networkName = null;
    }

    public boolean isRenderVisible() {
        return renderVisible;
    }

    public void setRenderVisible(boolean visible) {
        renderVisible = visible;
    }

    public String getNodeLabel() {
        return nodeLabel;
    }

    public void setNodeLabel(String label) {
        nodeLabel = trim(label, 48);
    }

    public String getChannelName(int channel) {
        return getChannelConfig(channel).name;
    }

    public void setChannelName(int channel, String name) {
        getChannelConfig(channel).name = trim(name, 24);
    }

    public boolean isChannelEnabled(int channel) {
        return getChannelConfig(channel).enabled;
    }

    public void setChannelEnabled(int channel, boolean enabled) {
        getChannelConfig(channel).enabled = enabled;
    }

    public ChannelMode getChannelMode(int channel) {
        return getChannelConfig(channel).mode;
    }

    public void setChannelMode(int channel, ChannelMode mode) {
        getChannelConfig(channel).mode = mode == null ? ChannelMode.IMPORT : mode;
    }

    public ChannelType getChannelType(int channel) {
        return getChannelConfig(channel).type;
    }

    public void setChannelType(int channel, ChannelType type) {
        ChannelConfig config = getChannelConfig(channel);
        ChannelType next = type == null ? ChannelType.ITEM : type;
        if (config.type != next) {
            Arrays.fill(filterItems[channel], ItemStack.EMPTY);
            config.type = next;
            config.batchSize = NodeUpgradeData.getOperationCap(next, getUpgradeTier());
            if (next == ChannelType.ENERGY) config.tickDelay = 1;
        }
    }

    @Nullable
    public Direction getChannelDirection(int channel) {
        return getChannelConfig(channel).ioDirection;
    }

    public void setChannelDirection(int channel, @Nullable Direction direction) {
        getChannelConfig(channel).ioDirection = direction;
    }

    public RedstoneMode getChannelRedstoneMode(int channel) {
        return getChannelConfig(channel).redstoneMode;
    }

    public void setChannelRedstoneMode(int channel, RedstoneMode mode) {
        getChannelConfig(channel).redstoneMode = mode == null ? RedstoneMode.LOW : mode;
    }

    public DistributionMode getChannelDistributionMode(int channel) {
        return getChannelConfig(channel).distributionMode;
    }

    public void setChannelDistributionMode(int channel, DistributionMode mode) {
        getChannelConfig(channel).distributionMode = mode == null ? DistributionMode.PRIORITY : mode;
    }

    public FilterMode getChannelFilterMode(int channel) {
        return getChannelConfig(channel).filterMode;
    }

    public void setChannelFilterMode(int channel, FilterMode mode) {
        getChannelConfig(channel).filterMode = mode == null ? FilterMode.MATCH_ANY : mode;
    }

    public boolean getChannelResourceRoundRobin(int channel) {
        return getChannelConfig(channel).resourceRoundRobin;
    }

    public void setChannelResourceRoundRobin(int channel, boolean value) {
        getChannelConfig(channel).resourceRoundRobin = value;
    }

    public int getChannelPriority(int channel) {
        return getChannelConfig(channel).priority;
    }

    public void setChannelPriority(int channel, int priority) {
        getChannelConfig(channel).priority = Math.max(-99, Math.min(99, priority));
    }

    public int getChannelBatchSize(int channel) {
        return getChannelConfig(channel).batchSize;
    }

    public void setChannelBatchSize(int channel, int batchSize) {
        ChannelConfig config = getChannelConfig(channel);
        int maximum = NodeUpgradeData.getOperationCap(config.type, getUpgradeTier());
        config.batchSize = config.type == ChannelType.ENERGY
                ? maximum : Math.max(1, Math.min(batchSize, maximum));
    }

    public int getChannelTickDelay(int channel) {
        return getChannelConfig(channel).tickDelay;
    }

    public void setChannelTickDelay(int channel, int delay) {
        ChannelConfig config = getChannelConfig(channel);
        config.tickDelay = config.type == ChannelType.ENERGY ? 1
                : Math.max(NodeUpgradeData.getMinTickDelay(getUpgradeTier()), Math.min(10_000, delay));
    }

    public ItemStack getFilterItem(int channel, int slot) {
        if (!isValidChannel(channel) || slot < 0 || slot >= ChannelData.FILTER_SIZE) {
            return ItemStack.EMPTY;
        }
        return filterItems[channel][slot];
    }

    public void setFilterItem(int channel, int slot, ItemStack stack) {
        if (!isValidChannel(channel) || slot < 0 || slot >= ChannelData.FILTER_SIZE) {
            return;
        }
        filterItems[channel][slot] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public ItemStack getUpgradeItem(int slot) {
        if (slot < 0 || slot >= upgradeItems.length) {
            return ItemStack.EMPTY;
        }
        return upgradeItems[slot];
    }

    public void setUpgradeItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= upgradeItems.length) {
            return;
        }
        ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (!next.isEmpty()) {
            for (int i = 0; i < upgradeItems.length; i++) {
                if (i != slot && ItemStack.isSameItem(upgradeItems[i], next)) return;
            }
        }
        int previousTier = getUpgradeTier();
        upgradeItems[slot] = next;
        int tier = getUpgradeTier();
        if (tier != previousTier) {
            for (ChannelConfig config : channels) {
                int maximum = NodeUpgradeData.getOperationCap(config.type, tier);
                config.batchSize = tier > previousTier ? maximum : Math.min(config.batchSize, maximum);
                if (tier < previousTier && config.type != ChannelType.ENERGY) {
                    config.tickDelay = Math.max(config.tickDelay, NodeUpgradeData.getMinTickDelay(tier));
                }
            }
        }
    }

    public int getUpgradeTier() {
        int tier = 0;
        for (ItemStack stack : upgradeItems) {
            tier = Math.max(tier, NodeUpgradeData.getUpgradeTier(stack));
        }
        return tier;
    }

    public int getEnabledChannelCount() {
        int count = 0;
        for (int channel = 0; channel < channels.length; channel++) {
            if (isChannelEnabled(channel)) {
                count++;
            }
        }
        return count;
    }

    public int getTotalFilterCount() {
        int count = 0;
        for (int channel = 0; channel < filterItems.length; channel++) {
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                if (!filterItems[channel][slot].isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getTotalUpgradeCount() {
        int count = 0;
        for (ItemStack stack : upgradeItems) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public int getFilterCountInChannel(int channel) {
        if (!isValidChannel(channel)) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
            if (!filterItems[channel][slot].isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public List<RequiredItem> getRequiredItemsPreview() {
        List<RequiredItem> result = new ArrayList<>();
        for (Requirement requirement : buildUpgradeRequirements(null)) {
            result.add(new RequiredItem(requirement.stack().copyWithCount(1), requirement.count()));
        }
        return result;
    }

    public boolean isEffectivelyEmpty() {
        if (!renderVisible) {
            return false;
        }
        if (networkId != null || (networkName != null && !networkName.isBlank())) {
            return false;
        }
        if (!nodeLabel.isEmpty()) {
            return false;
        }

        ChannelConfig defaults = defaultChannelConfig();
        for (int channel = 0; channel < channels.length; channel++) {
            ChannelConfig config = getChannelConfig(channel);
            if (config.enabled != defaults.enabled
                    || config.mode != defaults.mode
                    || config.type != defaults.type
                    || config.batchSize != defaults.batchSize
                    || config.tickDelay != defaults.tickDelay
                    || config.ioDirection != defaults.ioDirection
                    || config.redstoneMode != defaults.redstoneMode
                    || config.distributionMode != defaults.distributionMode
                    || config.filterMode != defaults.filterMode
                    || config.resourceRoundRobin
                    || config.priority != defaults.priority
                    || !config.name.isEmpty()) {
                return false;
            }
        }

        for (int channel = 0; channel < filterItems.length; channel++) {
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                if (!filterItems[channel][slot].isEmpty()) {
                    return false;
                }
            }
        }

        for (ItemStack stack : upgradeItems) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public boolean isStructurallyValid() {
        if (channels.length != LogisticsNodeEntity.CHANNEL_COUNT
                || filterItems.length != LogisticsNodeEntity.CHANNEL_COUNT
                || upgradeItems.length != LogisticsNodeEntity.UPGRADE_SLOT_COUNT) {
            return false;
        }

        for (int channel = 0; channel < channels.length; channel++) {
            if (channels[channel] == null || filterItems[channel] == null
                    || filterItems[channel].length != ChannelData.FILTER_SIZE) {
                return false;
            }

            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack stack = filterItems[channel][slot];
                if (stack != null && !stack.isEmpty() && !stack.is(ModTags.FILTERS)) {
                    return false;
                }
            }
        }

        for (ItemStack stack : upgradeItems) {
            if (stack != null && !stack.isEmpty() && !stack.is(ModTags.UPGRADES)) {
                return false;
            }
        }

        for (int first = 0; first < upgradeItems.length; first++) {
            if (upgradeItems[first] == null || upgradeItems[first].isEmpty()) continue;
            for (int second = first + 1; second < upgradeItems.length; second++) {
                if (ItemStack.isSameItem(upgradeItems[first], upgradeItems[second])) return false;
            }
        }

        return true;
    }

    private ChannelConfig getChannelConfig(int channel) {
        if (!isValidChannel(channel)) {
            return defaultChannelConfig();
        }
        if (channels[channel] == null) {
            channels[channel] = defaultChannelConfig();
        }
        return channels[channel];
    }

    private boolean isValidChannel(int channel) {
        return channel >= 0 && channel < channels.length;
    }

    public static NodeClipboardConfig fromNode(LogisticsNodeEntity node) {
        ChannelConfig[] channels = new ChannelConfig[LogisticsNodeEntity.CHANNEL_COUNT];
        ItemStack[][] filters = new ItemStack[LogisticsNodeEntity.CHANNEL_COUNT][ChannelData.FILTER_SIZE];
        ItemStack[] upgrades = new ItemStack[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];
        UUID networkId = node.getNetworkId();
        String networkName = node.getNetworkName();

        for (int channelIndex = 0; channelIndex < LogisticsNodeEntity.CHANNEL_COUNT; channelIndex++) {
            ChannelData channel = node.getChannel(channelIndex);
            ChannelConfig config = new ChannelConfig();
            if (channel != null) {
                config.enabled = channel.isEnabled();
                config.mode = channel.getMode();
                config.type = channel.getType();
                config.batchSize = channel.getBatchSize();
                config.tickDelay = channel.getTickDelay();
                config.ioDirection = channel.getIoDirection();
                config.redstoneMode = channel.getRedstoneMode();
                config.distributionMode = channel.getDistributionMode();
                config.filterMode = channel.getFilterMode();
                config.resourceRoundRobin = channel.isResourceRoundRobin();
                config.priority = channel.getPriority();
                config.name = channel.getName();

                for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                    ItemStack stack = channel.getFilterItem(slot);
                    filters[channelIndex][slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                }
            } else {
                config.enabled = false;
                config.mode = ChannelMode.IMPORT;
                config.type = ChannelType.ITEM;
                config.batchSize = 8;
                config.tickDelay = 20;
                config.ioDirection = Direction.UP;
                config.redstoneMode = RedstoneMode.LOW;
                config.distributionMode = DistributionMode.PRIORITY;
                config.filterMode = FilterMode.MATCH_ANY;
                config.priority = 0;

                Arrays.fill(filters[channelIndex], ItemStack.EMPTY);
            }
            channels[channelIndex] = config;
        }

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack stack = node.getUpgradeItem(slot);
            upgrades[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }

        if ((networkName == null || networkName.isBlank()) && networkId != null
                && node.level() instanceof ServerLevel serverLevel) {
            LogisticsNetwork network = NetworkRegistry.get(serverLevel).getNetwork(networkId);
            if (network != null) {
                networkName = network.getName();
            } else {
                networkName = "Network-" + networkId.toString().substring(0, 6);
            }
        }

        NodeClipboardConfig result = new NodeClipboardConfig(channels, filters, upgrades, networkId, networkName);
        result.renderVisible = node.isRenderVisible();
        result.nodeLabel = node.getNodeLabel();
        return result;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        var ops = provider == null ? NbtOps.INSTANCE : provider.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_VERSION, VERSION);
        if (networkId != null) {
            root.putString(KEY_NETWORK_ID, networkId.toString());
        }
        if (networkName != null && !networkName.isBlank()) {
            root.putString(KEY_NETWORK_NAME, networkName);
        }

        ListTag channelsTag = new ListTag();
        for (int channelIndex = 0; channelIndex < channels.length; channelIndex++) {
            ChannelConfig channel = channels[channelIndex];
            CompoundTag channelTag = new CompoundTag();
            channelTag.putInt(KEY_INDEX, channelIndex);
            channelTag.putBoolean(KEY_ENABLED, channel.enabled);
            channelTag.putString(KEY_MODE, channel.mode.name());
            channelTag.putString(KEY_TYPE, channel.type.name());
            channelTag.putInt(KEY_BATCH, channel.batchSize);
            channelTag.putInt(KEY_DELAY, channel.tickDelay);
            channelTag.putString(KEY_IO, channel.ioDirection != null ? channel.ioDirection.getName() : "all");
            channelTag.putString(KEY_REDSTONE, channel.redstoneMode.name());
            channelTag.putString(KEY_DISTRIBUTION, channel.distributionMode.name());
            channelTag.putString(KEY_FILTER_MODE, channel.filterMode.name());
            channelTag.putBoolean("resource_round_robin", channel.resourceRoundRobin);
            channelTag.putInt(KEY_PRIORITY, channel.priority);
            if (!channel.name.isEmpty())
                channelTag.putString(KEY_CH_NAME, channel.name);
            channelsTag.add(channelTag);
        }
        root.put(KEY_CHANNELS, channelsTag);
        root.putBoolean(KEY_VISIBLE, renderVisible);
        if (!nodeLabel.isEmpty()) {
            root.putString(KEY_NODE_LABEL, nodeLabel);
        }

        ListTag filtersTag = new ListTag();
        for (int channelIndex = 0; channelIndex < filterItems.length; channelIndex++) {
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack stack = filterItems[channelIndex][slot];
                if (stack.isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_CHANNEL, channelIndex);
                entry.putInt(KEY_SLOT, slot);
                entry.store(KEY_ITEM, ItemStack.OPTIONAL_CODEC, ops, stack);
                filtersTag.add(entry);
            }
        }
        if (!filtersTag.isEmpty()) {
            root.put(KEY_FILTERS, filtersTag);
        }

        ListTag upgradesTag = new ListTag();
        for (int slot = 0; slot < upgradeItems.length; slot++) {
            ItemStack stack = upgradeItems[slot];
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SLOT, slot);
            entry.store(KEY_ITEM, ItemStack.OPTIONAL_CODEC, ops, stack);
            upgradesTag.add(entry);
        }
        if (!upgradesTag.isEmpty()) {
            root.put(KEY_UPGRADES, upgradesTag);
        }

        ListTag requiredTag = new ListTag();
        for (Requirement requirement : buildUpgradeRequirements(null)) {
            CompoundTag entry = new CompoundTag();
            entry.store(KEY_ITEM, ItemStack.OPTIONAL_CODEC, ops, requirement.stack());
            entry.putInt(KEY_COUNT, requirement.count());
            requiredTag.add(entry);
        }
        if (!requiredTag.isEmpty()) {
            root.put(KEY_REQUIRED_ITEMS, requiredTag);
        }

        return root;
    }

    public static boolean canDecodeItems(CompoundTag root, @Nullable HolderLookup.Provider provider) {
        var ops = provider == null ? NbtOps.INSTANCE : provider.createSerializationContext(NbtOps.INSTANCE);
        for (String key : List.of(KEY_FILTERS, KEY_UPGRADES)) {
            for (Tag tag : root.getListOrEmpty(key)) {
                if (!(tag instanceof CompoundTag entry) || !entry.contains(KEY_ITEM)) continue;
                int slot = entry.getIntOr(KEY_SLOT, -1);
                int limit = key.equals(KEY_FILTERS) ? ChannelData.FILTER_SIZE : LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
                if (slot < 0 || slot >= limit) continue;
                if (key.equals(KEY_FILTERS)) {
                    int channel = entry.getIntOr(KEY_CHANNEL, -1);
                    if (channel < 0 || channel >= LogisticsNodeEntity.CHANNEL_COUNT) continue;
                }
                if (ItemStack.OPTIONAL_CODEC.parse(ops, entry.get(KEY_ITEM)).result().isEmpty()) return false;
            }
        }
        return true;
    }

    public static NodeClipboardConfig load(CompoundTag root, HolderLookup.Provider provider) {
        if (root == null || root.isEmpty() || !canDecodeItems(root, provider)) {
            return null;
        }

        if (root.contains(KEY_VERSION) && root.getIntOr(KEY_VERSION, VERSION) != VERSION) {
            return null;
        }

        var ops = provider == null ? NbtOps.INSTANCE : provider.createSerializationContext(NbtOps.INSTANCE);
        ChannelConfig[] channels = new ChannelConfig[LogisticsNodeEntity.CHANNEL_COUNT];
        ItemStack[][] filters = new ItemStack[LogisticsNodeEntity.CHANNEL_COUNT][ChannelData.FILTER_SIZE];
        ItemStack[] upgrades = new ItemStack[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelConfig defaults = defaultChannelConfig();
            channels[i] = defaults;
            Arrays.fill(filters[i], ItemStack.EMPTY);
        }
        Arrays.fill(upgrades, ItemStack.EMPTY);
        UUID networkId = parseOptionalUuid(root.getStringOr(KEY_NETWORK_ID, null));
        String networkName = root.contains(KEY_NETWORK_NAME) ? root.getStringOr(KEY_NETWORK_NAME, null) : null;
        if (networkName != null && networkName.isBlank()) {
            networkName = null;
        }

        if (!root.contains(KEY_CHANNELS)) {
            return null;
        }

        ListTag channelsTag = root.getListOrEmpty(KEY_CHANNELS);
        for (Tag tag : channelsTag) {
            if (!(tag instanceof CompoundTag channelTag)) {
                continue;
            }
            int index = channelTag.getIntOr(KEY_INDEX, -1);
            if (index < 0 || index >= LogisticsNodeEntity.CHANNEL_COUNT) {
                continue;
            }

            ChannelConfig config = defaultChannelConfig();
            config.enabled = channelTag.getBooleanOr(KEY_ENABLED, false);
            config.mode = parseEnum(channelTag.getStringOr(KEY_MODE, ChannelMode.IMPORT.name()), ChannelMode.values(), ChannelMode.IMPORT);
            config.type = parseEnum(channelTag.getStringOr(KEY_TYPE, ChannelType.ITEM.name()), ChannelType.values(), ChannelType.ITEM);
            config.batchSize = Math.max(1, channelTag.getIntOr(KEY_BATCH, 8));
            config.tickDelay = config.type == ChannelType.ENERGY ? 1 : Math.max(1, channelTag.getIntOr(KEY_DELAY, 20));

            String dirStr = channelTag.getStringOr(KEY_IO, Direction.UP.getName());
            if ("all".equals(dirStr)) {
                config.ioDirection = null;
            } else {
                Direction direction = Direction.byName(dirStr);
                config.ioDirection = direction == null ? Direction.UP : direction;
            }
            String savedRedstoneMode = channelTag.getStringOr(KEY_REDSTONE, "");
            if (RedstoneMode.disablesChannel(savedRedstoneMode))
                config.enabled = false;
            config.redstoneMode = RedstoneMode.fromSerialized(savedRedstoneMode);
            config.distributionMode = parseEnum(channelTag.getStringOr(KEY_DISTRIBUTION, DistributionMode.PRIORITY.name()), DistributionMode.values(),
                    DistributionMode.PRIORITY);
            config.resourceRoundRobin = channelTag.getBooleanOr("resource_round_robin", false);
            config.filterMode = parseEnum(channelTag.getStringOr(KEY_FILTER_MODE, FilterMode.MATCH_ANY.name()), FilterMode.values(),
                    FilterMode.MATCH_ANY);
            config.priority = Math.max(-99, Math.min(99, channelTag.getIntOr(KEY_PRIORITY, 0)));
            if (channelTag.contains(KEY_CH_NAME))
                config.name = channelTag.getStringOr(KEY_CH_NAME, "");
            channels[index] = config;
        }

        if (root.contains(KEY_FILTERS)) {
            ListTag filtersTag = root.getListOrEmpty(KEY_FILTERS);
            for (Tag tag : filtersTag) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                int channel = entry.getIntOr(KEY_CHANNEL, -1);
                int slot = entry.getIntOr(KEY_SLOT, -1);
                if (channel < 0 || channel >= LogisticsNodeEntity.CHANNEL_COUNT || slot < 0
                        || slot >= ChannelData.FILTER_SIZE) {
                    continue;
                }

                ItemStack stack = entry.read(KEY_ITEM, ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
                filters[channel][slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            }
        }

        if (root.contains(KEY_UPGRADES)) {
            ListTag upgradesTag = root.getListOrEmpty(KEY_UPGRADES);
            for (Tag tag : upgradesTag) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                int slot = entry.getIntOr(KEY_SLOT, -1);
                if (slot < 0 || slot >= LogisticsNodeEntity.UPGRADE_SLOT_COUNT) {
                    continue;
                }

                ItemStack stack = entry.read(KEY_ITEM, ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
                upgrades[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            }
        }

        NodeClipboardConfig config = new NodeClipboardConfig(channels, filters, upgrades, networkId, networkName);
        if (root.contains(KEY_VISIBLE)) {
            config.renderVisible = root.getBooleanOr(KEY_VISIBLE, config.renderVisible);
        }
        if (root.contains(KEY_NODE_LABEL)) {
            config.nodeLabel = root.getStringOr(KEY_NODE_LABEL, "");
        }
        return config.isStructurallyValid() ? config : null;
    }

    public PasteOutcome applyToNode(ServerPlayer player, LogisticsNodeEntity node, ItemStack protectedStack) {
        return applyToNode(player, node, protectedStack, (StorageLink) null);
    }

    public PasteOutcome applyToNode(ServerPlayer player, LogisticsNodeEntity node, ItemStack protectedStack,
                                   @Nullable StorageLink storageLink) {
        if (player == null || node == null || channels.length != LogisticsNodeEntity.CHANNEL_COUNT) {
            return new PasteOutcome(PasteResult.CLIPBOARD_INVALID);
        }

        if (!isStructurallyValid()) {
            return new PasteOutcome(PasteResult.CLIPBOARD_INVALID);
        }

        if (!hasCompatibleStructure(node)) {
            return new PasteOutcome(PasteResult.INCOMPATIBLE_TARGET);
        }
        if (!canAccessTargetNetwork(player, node)) {
            return new PasteOutcome(PasteResult.INCOMPATIBLE_TARGET);
        }

        Inventory inventory = player.getInventory();
        int protectedSlot = findProtectedSlot(inventory, protectedStack);
        List<Requirement> requirements = buildUpgradeRequirements(node);
        List<ItemStack> returnedItems = collectReturnedItems(node);

        StorageAccess access = storageLink == null ? null : LinkedStorage.resolve(player.level(), storageLink);
        if (access != null && !access.allows(player, StorageAction.EXTRACT)) access = null;
        List<RequiredItem> missingItems = new ArrayList<>();
        for (Requirement requirement : requirements) {
            int available = StorageInventory.count(inventory, requirement.stack(), protectedSlot);
            long stored = access == null ? 0 : access.count(requirement.stack());
            long missing = requirement.count() - (long) available - stored;
            if (missing > 0) {
                missingItems.add(new RequiredItem(requirement.stack().copyWithCount(1), (int) missing));
            }
        }
        if (!missingItems.isEmpty()) {
            return new PasteOutcome(PasteResult.MISSING_ITEMS, missingItems);
        }

        List<ItemStack> inventoryReserved = new ArrayList<>();
        List<ItemStack> storageReserved = new ArrayList<>();
        for (Requirement requirement : requirements) {
            List<ItemStack> fromInventory = StorageInventory.reserve(
                    inventory, requirement.stack(), requirement.count(), protectedSlot);
            inventoryReserved.addAll(fromInventory);
            int remaining = requirement.count() - StorageInventory.count(fromInventory);
            List<ItemStack> fromStorage = access == null ? List.of()
                    : access.extract(requirement.stack(), remaining, player);
            storageReserved.addAll(fromStorage);
            int extracted = StorageInventory.count(fromStorage);
            if (extracted != remaining) {
                StorageInventory.returnToPlayer(player, inventoryReserved);
                if (access == null) StorageInventory.returnToPlayer(player, storageReserved);
                else StorageInventory.returnToStorageOrPlayer(access, player, storageReserved);
                return new PasteOutcome(PasteResult.MISSING_ITEMS, List.of(new RequiredItem(
                        requirement.stack().copyWithCount(1), Math.max(1, remaining - extracted))));
            }
        }
        applyToNode(node);
        applyNetworkToNode(node, player);
        List<ItemStack> leftovers = returnItemsToInventory(inventory, returnedItems, protectedSlot);
        for (ItemStack leftover : leftovers) {
            player.drop(leftover, false);
        }
        inventory.setChanged();

        return new PasteOutcome(PasteResult.SUCCESS);
    }

    public PasteResult applyToNodeWithoutInventory(ServerPlayer player, LogisticsNodeEntity node) {
        if (node == null || channels.length != LogisticsNodeEntity.CHANNEL_COUNT) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!isStructurallyValid()) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!hasCompatibleStructure(node)) {
            return PasteResult.INCOMPATIBLE_TARGET;
        }
        if (!canAccessTargetNetwork(player, node)) {
            return PasteResult.INCOMPATIBLE_TARGET;
        }

        applyToNode(node);
        applyNetworkToNode(node, player);
        return PasteResult.SUCCESS;
    }

    private boolean hasCompatibleStructure(LogisticsNodeEntity node) {
        for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
            if (node.getChannel(channel) == null) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> collectReturnedItems(LogisticsNodeEntity node) {
        List<ItemStack> returnedItems = new ArrayList<>();

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack expected = upgradeItems[slot];
            ItemStack current = node.getUpgradeItem(slot);
            if (shouldReturnReplacedItem(expected, current)) {
                returnedItems.add(current.copy());
            }
        }

        return returnedItems;
    }

    private static boolean shouldReturnReplacedItem(ItemStack expected, ItemStack current) {
        if (current.isEmpty()) {
            return false;
        }
        if (expected.isEmpty()) {
            return true;
        }
        // Same base item can be reconfigured in place without consuming or returning another item.
        return !ItemStack.isSameItem(expected, current);
    }

    private boolean canAccessTargetNetwork(ServerPlayer player, LogisticsNodeEntity node) {
        if (networkId == null || !(node.level() instanceof ServerLevel serverLevel)) return true;
        LogisticsNetwork network = NetworkRegistry.get(serverLevel).getNetwork(networkId);
        return network == null || NodeAccessPolicy.canAccess(network.getOwnerUuid(), player);
    }

    private void applyNetworkToNode(LogisticsNodeEntity node, ServerPlayer player) {
        if (!(node.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        NetworkRegistry registry = NetworkRegistry.get(serverLevel);
        UUID currentNetworkId = node.getNetworkId();

        if (networkId == null && (networkName == null || networkName.isBlank())) {
            if (currentNetworkId != null) {
                registry.removeNodeFromNetwork(currentNetworkId, node.getUUID());
                node.setNetworkId(null);
            }
            node.setNetworkName("");
            return;
        }

        LogisticsNetwork targetNetwork = resolveTargetNetwork(registry, node.getOwnerUUID(), player);
        if (targetNetwork == null) {
            return;
        }

        UUID targetNetworkId = targetNetwork.getId();
        if (currentNetworkId != null && !currentNetworkId.equals(targetNetworkId)) {
            registry.removeNodeFromNetwork(currentNetworkId, node.getUUID());
        }

        node.setNetworkId(targetNetworkId);
        node.setNetworkName(targetNetwork.getName());
        node.setNetworkColor(targetNetwork.getColor());
        registry.addNodeToNetwork(targetNetworkId, node.getUUID());

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData ch = node.getChannel(i);
            if (ch != null) {
                ch.setName(targetNetwork.getChannelName(i));
            }
        }
    }

    @Nullable
    private LogisticsNetwork resolveTargetNetwork(NetworkRegistry registry, UUID ownerUuid, ServerPlayer player) {
        if (networkId != null) {
            LogisticsNetwork byId = registry.getNetwork(networkId);
            if (byId != null && NodeAccessPolicy.canAccess(byId.getOwnerUuid(), player)) {
                return byId;
            }
            if (byId != null) return null;
        }

        if (networkName != null && !networkName.isBlank()) {
            LogisticsNetwork created = registry.createNetwork(networkName, ownerUuid);
            networkId = created.getId();
            networkName = created.getName();
            seedChannelNames(created);
            return created;
        }

        if (networkId != null) {
            LogisticsNetwork created = registry.createNetwork("Network-" + networkId.toString().substring(0, 6), ownerUuid);
            networkId = created.getId();
            networkName = created.getName();
            seedChannelNames(created);
            return created;
        }

        return null;
    }

    private void seedChannelNames(LogisticsNetwork network) {
        for (int channel = 0; channel < channels.length; channel++) {
            network.setChannelName(channel, channels[channel].name);
        }
    }

    private static String trim(@Nullable String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private List<Requirement> buildUpgradeRequirements(LogisticsNodeEntity node) {
        List<Requirement> requirements = new ArrayList<>();

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack required = upgradeItems[slot];
            if (required.isEmpty()) {
                continue;
            }

            if (node != null && ItemStack.isSameItem(required, node.getUpgradeItem(slot))) {
                continue;
            }
            addRequirement(requirements, required);
        }

        return requirements;
    }

    private static void addRequirement(List<Requirement> requirements, ItemStack stack) {
        for (int i = 0; i < requirements.size(); i++) {
            Requirement requirement = requirements.get(i);
            if (ItemStack.isSameItem(requirement.stack(), stack)) {
                requirements.set(i, new Requirement(requirement.stack(), requirement.count() + 1));
                return;
            }
        }
        requirements.add(new Requirement(stack.copyWithCount(1), 1));
    }

    private static int findProtectedSlot(Inventory inventory, ItemStack protectedStack) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == protectedStack) {
                return slot;
            }
        }
        return -1;
    }

    private static List<ItemStack> returnItemsToInventory(Inventory inventory, List<ItemStack> returnedItems,
            int protectedSlot) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : returnedItems) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = stack.copy();
            insertIntoInventory(inventory, remaining, protectedSlot);
            if (!remaining.isEmpty()) {
                leftovers.add(remaining);
            }
        }
        return leftovers;
    }

    private static void insertIntoInventory(Inventory inventory, ItemStack remaining, int protectedSlot) {
        for (int slot = 0; slot < inventory.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) {
                continue;
            }
            int max = Math.min(current.getMaxStackSize(), remaining.getMaxStackSize());
            int room = max - current.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, remaining.getCount());
            current.grow(move);
            inventory.setItem(slot, current);
            remaining.shrink(move);
        }

        for (int slot = 0; slot < inventory.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }
            int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            inventory.setItem(slot, remaining.copyWithCount(move));
            remaining.shrink(move);
        }
    }

    private void applyToNode(LogisticsNodeEntity node) {
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack expected = upgradeItems[slot];
            ItemStack current = node.getUpgradeItem(slot);

            if (expected.isEmpty()) {
                node.setUpgradeItem(slot, ItemStack.EMPTY);
            } else if (!ItemStack.isSameItemSameComponents(expected, current)) {
                node.setUpgradeItem(slot, expected.copyWithCount(1));
            }
        }

        for (int channelIndex = 0; channelIndex < LogisticsNodeEntity.CHANNEL_COUNT; channelIndex++) {
            ChannelData channel = node.getChannel(channelIndex);
            ChannelConfig config = channels[channelIndex];
            if (channel == null || config == null) {
                continue;
            }

            channel.setEnabled(config.enabled);
            channel.setMode(config.mode);
            channel.setType(config.type);
            channel.setBatchSize(config.batchSize);
            channel.setTickDelay(config.tickDelay);
            channel.setIoDirection(config.ioDirection);
            channel.setRedstoneMode(config.redstoneMode);
            channel.setDistributionMode(config.distributionMode);
            channel.setFilterMode(config.filterMode);
            channel.setResourceRoundRobin(config.resourceRoundRobin);
            channel.resetResourceRotation();
            channel.setPriority(config.priority);
            channel.setName(config.name);

            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack expected = filterItems[channelIndex][slot];
                ItemStack current = channel.getFilterItem(slot);

                if (expected.isEmpty()) {
                    channel.setFilterItem(slot, ItemStack.EMPTY);
                } else if (!ItemStack.isSameItemSameComponents(expected, current)) {
                    channel.setFilterItem(slot, expected.copyWithCount(1));
                }
            }
        }

        node.setRenderVisible(renderVisible);
        node.setNodeLabel(nodeLabel);
    }

    private static ChannelConfig defaultChannelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.enabled = false;
        config.mode = ChannelMode.IMPORT;
        config.type = ChannelType.ITEM;
        config.batchSize = 8;
        config.tickDelay = 20;
        config.ioDirection = Direction.UP;
        config.redstoneMode = RedstoneMode.LOW;
        config.distributionMode = DistributionMode.PRIORITY;
        config.filterMode = FilterMode.MATCH_ANY;
        config.priority = 0;
        return config;
    }

    private static <E extends Enum<E>> E parseEnum(String value, E[] values, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        for (E candidate : values) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        return fallback;
    }

    @Nullable
    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
