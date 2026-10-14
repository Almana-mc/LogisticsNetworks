package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.util.ItemStackCompat;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class NodeClipboardConfig {

    private static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_UPGRADES = "upgrades";
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
    private static final String KEY_RESOURCE_ROUND_ROBIN = "resource_round_robin";
    // 1.3.4 clipboards held 9 filters
    private static final int LEGACY_FILTER_SLOTS = 9;
    private static final String KEY_VISIBLE = "renderVisible";
    private static final String KEY_NODE_LABEL = "node_label";

    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_ITEM = "item";

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
        @Nullable Direction ioDirection;
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

    public @Nullable Direction getChannelDirection(int channel) {
        return getChannelConfig(channel).ioDirection;
    }

    public void setChannelDirection(int channel, @Nullable Direction direction) {
        getChannelConfig(channel).ioDirection = direction;
    }

    public RedstoneMode getChannelRedstoneMode(int channel) {
        return getChannelConfig(channel).redstoneMode;
    }

    public void setChannelRedstoneMode(int channel, RedstoneMode mode) {
        getChannelConfig(channel).redstoneMode = mode == null ? RedstoneMode.IGNORED : mode;
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
        filterItems[channel][slot] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
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
        ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
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

    public void stripUpgradeComponents() {
        for (int slot = 0; slot < upgradeItems.length; slot++) {
            setUpgradeItem(slot, new ItemStack(upgradeItems[slot].getItem()));
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
            result.add(new RequiredItem(ItemStackCompat.copyWithCount(requirement.stack(), 1), requirement.count()));
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
                    || !config.name.equals(defaults.name)) {
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
                    filters[channelIndex][slot] = stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
                }
            } else {
                config.enabled = false;
                config.mode = ChannelMode.IMPORT;
                config.type = ChannelType.ITEM;
                config.batchSize = 8;
                config.tickDelay = 20;
                config.ioDirection = Direction.UP;
                config.redstoneMode = RedstoneMode.IGNORED;
                config.distributionMode = DistributionMode.PRIORITY;
                config.filterMode = FilterMode.MATCH_ANY;
                config.priority = 0;

                Arrays.fill(filters[channelIndex], ItemStack.EMPTY);
            }
            channels[channelIndex] = config;
        }

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack stack = node.getUpgradeItem(slot);
            upgrades[slot] = stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
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
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_VERSION, VERSION);
        if (networkId != null) {
            root.putUUID(KEY_NETWORK_ID, networkId);
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
            channelTag.putInt(KEY_PRIORITY, channel.priority);
            if (channel.resourceRoundRobin)
                channelTag.putBoolean(KEY_RESOURCE_ROUND_ROBIN, true);
            if (!channel.name.isEmpty())
                channelTag.putString("Name", channel.name);
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
                entry.put(KEY_ITEM, ItemStackCompat.save(stack, provider));
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
            entry.put(KEY_ITEM, ItemStackCompat.save(stack, provider));
            upgradesTag.add(entry);
        }
        if (!upgradesTag.isEmpty()) {
            root.put(KEY_UPGRADES, upgradesTag);
        }

        return root;
    }

    // Fits 32 KB serverbound cap
    public CompoundTag saveSettings(HolderLookup.Provider provider) {
        CompoundTag root = save(provider);
        root.remove(KEY_FILTERS);
        return root;
    }

    public static NodeClipboardConfig load(CompoundTag root, HolderLookup.Provider provider) {
        if (root == null || root.isEmpty()) {
            return null;
        }

        if (root.contains(KEY_VERSION, Tag.TAG_INT) && root.getInt(KEY_VERSION) != VERSION || isOversized(root)) {
            return null;
        }

        ChannelConfig[] channels = new ChannelConfig[LogisticsNodeEntity.CHANNEL_COUNT];
        ItemStack[][] filters = new ItemStack[LogisticsNodeEntity.CHANNEL_COUNT][ChannelData.FILTER_SIZE];
        ItemStack[] upgrades = new ItemStack[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelConfig defaults = defaultChannelConfig();
            channels[i] = defaults;
            Arrays.fill(filters[i], ItemStack.EMPTY);
        }
        Arrays.fill(upgrades, ItemStack.EMPTY);
        UUID networkId = root.hasUUID(KEY_NETWORK_ID) ? root.getUUID(KEY_NETWORK_ID) : null;
        String networkName = root.contains(KEY_NETWORK_NAME, Tag.TAG_STRING) ? root.getString(KEY_NETWORK_NAME) : null;
        if (networkName != null && networkName.isBlank()) {
            networkName = null;
        }

        if (!root.contains(KEY_CHANNELS, Tag.TAG_LIST)) {
            return null;
        }

        ListTag channelsTag = root.getList(KEY_CHANNELS, Tag.TAG_COMPOUND);
        for (Tag tag : channelsTag) {
            if (!(tag instanceof CompoundTag channelTag)) {
                continue;
            }
            int index = channelTag.getInt(KEY_INDEX);
            if (index < 0 || index >= LogisticsNodeEntity.CHANNEL_COUNT) {
                continue;
            }

            ChannelConfig config = defaultChannelConfig();
            config.enabled = channelTag.getBoolean(KEY_ENABLED);
            config.mode = parseEnum(channelTag.getString(KEY_MODE), ChannelMode.values(), ChannelMode.IMPORT);
            config.type = parseEnum(channelTag.getString(KEY_TYPE), ChannelType.values(), ChannelType.ITEM);
            config.batchSize = Math.max(1, channelTag.getInt(KEY_BATCH));
            config.tickDelay = Math.max(1, channelTag.getInt(KEY_DELAY));

            String ioStr = channelTag.getString(KEY_IO);
            if ("all".equals(ioStr)) {
                config.ioDirection = null;
            } else {
                Direction direction = Direction.byName(ioStr);
                config.ioDirection = direction == null ? Direction.UP : direction;
            }
            String redstone = channelTag.getString(KEY_REDSTONE);
            config.redstoneMode = RedstoneMode.fromSerialized(redstone);
            if (RedstoneMode.disablesChannel(redstone))
                config.enabled = false;
            config.distributionMode = parseEnum(channelTag.getString(KEY_DISTRIBUTION), DistributionMode.values(),
                    DistributionMode.PRIORITY);
            config.filterMode = parseEnum(channelTag.getString(KEY_FILTER_MODE), FilterMode.values(),
                    FilterMode.MATCH_ANY);
            config.priority = Math.max(-99, Math.min(99, channelTag.getInt(KEY_PRIORITY)));
            config.name = channelTag.contains("Name") ? channelTag.getString("Name") : "";
            config.resourceRoundRobin = channelTag.getBoolean(KEY_RESOURCE_ROUND_ROBIN);
            channels[index] = config;
        }

        if (root.contains(KEY_FILTERS, Tag.TAG_LIST)) {
            ListTag filtersTag = root.getList(KEY_FILTERS, Tag.TAG_COMPOUND);
            for (Tag tag : filtersTag) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                int channel = entry.getInt(KEY_CHANNEL);
                int slot = entry.getInt(KEY_SLOT);
                if (channel < 0 || channel >= LogisticsNodeEntity.CHANNEL_COUNT || slot < 0
                        || slot >= ChannelData.FILTER_SIZE || !entry.contains(KEY_ITEM, Tag.TAG_COMPOUND)) {
                    continue;
                }

                ItemStack stack = ItemStackCompat.parseOptional(provider, entry.getCompound(KEY_ITEM));
                filters[channel][slot] = stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
            }
        }

        if (root.contains(KEY_UPGRADES, Tag.TAG_LIST)) {
            ListTag upgradesTag = root.getList(KEY_UPGRADES, Tag.TAG_COMPOUND);
            for (Tag tag : upgradesTag) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                int slot = entry.getInt(KEY_SLOT);
                if (slot < 0 || slot >= LogisticsNodeEntity.UPGRADE_SLOT_COUNT
                        || !entry.contains(KEY_ITEM, Tag.TAG_COMPOUND)) {
                    continue;
                }

                ItemStack stack = ItemStackCompat.parseOptional(provider, entry.getCompound(KEY_ITEM));
                upgrades[slot] = stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
            }
        }

        NodeClipboardConfig config = new NodeClipboardConfig(channels, filters, upgrades, networkId, networkName);
        if (root.contains(KEY_VISIBLE, Tag.TAG_BYTE)) {
            config.renderVisible = root.getBoolean(KEY_VISIBLE);
        }
        if (root.contains(KEY_NODE_LABEL, Tag.TAG_STRING)) {
            config.nodeLabel = root.getString(KEY_NODE_LABEL);
        }
        config.sanitizeText();
        return config.isStructurallyValid() ? config : null;
    }

    private static boolean isOversized(CompoundTag root) {
        return size(root, KEY_CHANNELS) > LogisticsNodeEntity.CHANNEL_COUNT
                || size(root, KEY_FILTERS) > LogisticsNodeEntity.CHANNEL_COUNT * LEGACY_FILTER_SLOTS
                || size(root, KEY_UPGRADES) > LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
    }

    private static int size(CompoundTag root, String key) {
        return root.get(key) instanceof CollectionTag<?> list ? list.size() : 0;
    }

    private void sanitizeText() {
        setNetworkTarget(networkId, networkName);
        setNodeLabel(nodeLabel);
        for (int channel = 0; channel < channels.length; channel++) {
            setChannelName(channel, channels[channel].name);
        }
    }

    public PasteOutcome applyToNode(ServerPlayer player, LogisticsNodeEntity node, ItemStack protectedStack) {
        return applyToNode(player, node, protectedStack, null);
    }

    public PasteOutcome applyToNode(ServerPlayer player, LogisticsNodeEntity node, ItemStack protectedStack,
                                   @Nullable GlobalPos ae2Link) {
        if (player == null || node == null || channels.length != LogisticsNodeEntity.CHANNEL_COUNT
                || player.hasDisconnected() || !player.isAlive()) {
            return new PasteOutcome(PasteResult.CLIPBOARD_INVALID);
        }

        if (!isStructurallyValid()) {
            return new PasteOutcome(PasteResult.CLIPBOARD_INVALID);
        }

        if (!hasCompatibleStructure(node) || !canAccessTargetNetwork(player, node)) {
            return new PasteOutcome(PasteResult.INCOMPATIBLE_TARGET);
        }

        Inventory inventory = player.getInventory();
        int protectedSlot = findProtectedSlot(inventory, protectedStack);
        List<Requirement> requirements = buildUpgradeRequirements(node);
        List<ItemStack> returnedItems = collectReturnedItems(node);

        List<RequiredItem> missingItems = findMissing(player, requirements, protectedSlot, ae2Link);
        if (!missingItems.isEmpty()) {
            return new PasteOutcome(PasteResult.MISSING_ITEMS, missingItems);
        }

        for (Requirement requirement : requirements) {
            AE2Compat.consumeCombined(inventory, requirement.stack(), requirement.count(),
                    protectedSlot, ae2Link, player);
        }
        applyToNode(node);
        applyNetworkToNode(node, player);
        List<ItemStack> leftovers = returnItemsToInventory(inventory, returnedItems, protectedSlot);
        for (ItemStack leftover : leftovers) {
            player.drop(leftover, false);
        }
        inventory.setChanged();
        syncPastedLabel(player, List.of(node));

        return new PasteOutcome(PasteResult.SUCCESS);
    }

    private static List<RequiredItem> findMissing(ServerPlayer player, List<Requirement> requirements,
            int protectedSlot, @Nullable GlobalPos ae2Link) {
        List<RequiredItem> missingItems = new ArrayList<>();
        for (Requirement requirement : requirements) {
            long available = AE2Compat.countInInventory(player.getInventory(), requirement.stack(), protectedSlot);
            if (ae2Link != null && available < requirement.count())
                available += AE2Compat.countAvailable(player.serverLevel(), ae2Link, requirement.stack());
            long missing = requirement.count() - available;
            if (missing > 0)
                missingItems.add(new RequiredItem(ItemStackCompat.copyWithCount(requirement.stack(), 1), (int) missing));
        }
        return missingItems;
    }

    public PasteResult applyToNodeWithoutInventory(ServerPlayer player, LogisticsNodeEntity node) {
        if (node == null || channels.length != LogisticsNodeEntity.CHANNEL_COUNT) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!isStructurallyValid()) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!hasCompatibleStructure(node) || !canAccessTargetNetwork(player, node)) {
            return PasteResult.INCOMPATIBLE_TARGET;
        }

        applyToNode(node);
        applyNetworkToNode(node, player);
        return PasteResult.SUCCESS;
    }

    public static void syncPastedLabel(ServerPlayer player, List<LogisticsNodeEntity> nodes) {
        LogisticsNodeEntity first = nodes.get(0);
        String label = first.getNodeLabel();
        if (label.isEmpty() || first.getNetworkId() == null) return;
        LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(first.getNetworkId());
        if (network == null) return;
        nodes.forEach(node -> node.setLabelRevision(0));
        LabelUpgradeSync.synchronizeLabels(player, network, nodes, first.getUUID(), label);
    }

    private boolean canAccessTargetNetwork(ServerPlayer player, LogisticsNodeEntity node) {
        if (networkId == null || !(node.level() instanceof ServerLevel serverLevel)) return true;
        LogisticsNetwork network = NetworkRegistry.get(serverLevel).getNetwork(networkId);
        return network == null || NodeAccessPolicy.canAccess(network.getOwnerUuid(), player);
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
        if (targetNetwork != null) {
            joinNetwork(node, registry, targetNetwork);
        }
    }

    public static void joinNetwork(LogisticsNodeEntity node, NetworkRegistry registry, LogisticsNetwork network) {
        UUID currentNetworkId = node.getNetworkId();
        if (currentNetworkId != null && !currentNetworkId.equals(network.getId())) {
            registry.removeNodeFromNetwork(currentNetworkId, node.getUUID());
        }

        node.setNetworkId(network.getId());
        node.setNetworkName(network.getName());
        node.setNetworkColor(network.getColor());
        registry.addNodeToNetwork(network.getId(), node.getUUID());

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData ch = node.getChannel(i);
            ch.setName(network.getChannelName(i));
            ServerPayloadHandler.sendChannelSyncToViewers(node, i, ch);
        }
    }

    @Nullable
    private LogisticsNetwork resolveTargetNetwork(NetworkRegistry registry, UUID ownerUuid, ServerPlayer player) {
        if (networkId != null) {
            LogisticsNetwork byId = registry.getNetwork(networkId);
            if (byId != null && NodeAccessPolicy.canAccess(byId.getOwnerUuid(), player)) {
                return byId;
            }
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
        requirements.add(new Requirement(ItemStackCompat.copyWithCount(stack, 1), 1));
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
            if (current.isEmpty() || !ItemStackCompat.isSameItemSameComponents(current, remaining)) {
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
            inventory.setItem(slot, ItemStackCompat.copyWithCount(remaining, move));
            remaining.shrink(move);
        }
    }

    public void applyToNode(LogisticsNodeEntity node) {
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack expected = upgradeItems[slot];
            ItemStack current = node.getUpgradeItem(slot);

            if (expected.isEmpty()) {
                node.setUpgradeItem(slot, ItemStack.EMPTY);
            } else if (!ItemStack.isSameItem(expected, current)) {
                node.setUpgradeItem(slot, new ItemStack(expected.getItem()));
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
                } else if (!ItemStackCompat.isSameItemSameComponents(expected, current)) {
                    channel.setFilterItem(slot, ItemStackCompat.copyWithCount(expected, 1));
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
        config.redstoneMode = RedstoneMode.IGNORED;
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
}


