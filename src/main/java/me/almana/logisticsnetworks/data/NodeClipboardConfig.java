package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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

    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_ITEM = "item";
    private static final String KEY_COUNT = "count";

    private final ChannelConfig[] channels;
    private final ItemStack[][] filterItems;
    private final ItemStack[] upgradeItems;
    @Nullable
    private UUID networkId;
    @Nullable
    private String networkName;
    private boolean renderVisible;

    public enum PasteResult {
        SUCCESS,
        CLIPBOARD_INVALID,
        INCOMPATIBLE_TARGET,
        MISSING_ITEMS,
        INVENTORY_FULL
    }

    private record Requirement(ItemStack stack, int count) {
    }

    public record RequiredItem(ItemStack stack, int count) {
    }

    private static final class ChannelConfig {
        boolean enabled;
        ChannelMode mode;
        ChannelType type;
        int batchSize;
        int tickDelay;
        Direction ioDirection;
        RedstoneMode redstoneMode;
        DistributionMode distributionMode;
        FilterMode filterMode;
        int priority;
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
        getChannelConfig(channel).type = type == null ? ChannelType.ITEM : type;
    }

    public Direction getChannelDirection(int channel) {
        return getChannelConfig(channel).ioDirection;
    }

    public void setChannelDirection(int channel, Direction direction) {
        getChannelConfig(channel).ioDirection = direction == null ? Direction.UP : direction;
    }

    public RedstoneMode getChannelRedstoneMode(int channel) {
        return getChannelConfig(channel).redstoneMode;
    }

    public void setChannelRedstoneMode(int channel, RedstoneMode mode) {
        getChannelConfig(channel).redstoneMode = mode == null ? RedstoneMode.ALWAYS_ON : mode;
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
        getChannelConfig(channel).batchSize = Math.max(1, batchSize);
    }

    public int getChannelTickDelay(int channel) {
        return getChannelConfig(channel).tickDelay;
    }

    public void setChannelTickDelay(int channel, int delay) {
        getChannelConfig(channel).tickDelay = Math.max(1, delay);
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
        upgradeItems[slot] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
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
        for (Requirement requirement : buildRequirements(null)) {
            result.add(new RequiredItem(requirement.stack().copyWithCount(1), requirement.count()));
        }
        return result;
    }

    public boolean isEffectivelyEmpty() {
        if (networkId != null || (networkName != null && !networkName.isBlank())) {
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
                    || config.priority != defaults.priority) {
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
                config.priority = channel.getPriority();

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
                config.redstoneMode = RedstoneMode.ALWAYS_ON;
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
            channelTag.putString(KEY_IO, channel.ioDirection.getName());
            channelTag.putString(KEY_REDSTONE, channel.redstoneMode.name());
            channelTag.putString(KEY_DISTRIBUTION, channel.distributionMode.name());
            channelTag.putString(KEY_FILTER_MODE, channel.filterMode.name());
            channelTag.putInt(KEY_PRIORITY, channel.priority);
            channelsTag.add(channelTag);
        }
        root.put(KEY_CHANNELS, channelsTag);
        root.putBoolean(KEY_VISIBLE, renderVisible);

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
                entry.put(KEY_ITEM, stack.save(provider));
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
            entry.put(KEY_ITEM, stack.save(provider));
            upgradesTag.add(entry);
        }
        if (!upgradesTag.isEmpty()) {
            root.put(KEY_UPGRADES, upgradesTag);
        }

        ListTag requiredTag = new ListTag();
        for (Requirement requirement : buildRequirements(null)) {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_ITEM, requirement.stack().save(provider));
            entry.putInt(KEY_COUNT, requirement.count());
            requiredTag.add(entry);
        }
        if (!requiredTag.isEmpty()) {
            root.put(KEY_REQUIRED_ITEMS, requiredTag);
        }

        return root;
    }

    public static NodeClipboardConfig load(CompoundTag root, HolderLookup.Provider provider) {
        if (root == null || root.isEmpty()) {
            return null;
        }

        if (root.contains(KEY_VERSION, Tag.TAG_INT) && root.getInt(KEY_VERSION) != VERSION) {
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

            Direction direction = Direction.byName(channelTag.getString(KEY_IO));
            config.ioDirection = direction == null ? Direction.UP : direction;
            config.redstoneMode = parseEnum(channelTag.getString(KEY_REDSTONE), RedstoneMode.values(),
                    RedstoneMode.ALWAYS_ON);
            config.distributionMode = parseEnum(channelTag.getString(KEY_DISTRIBUTION), DistributionMode.values(),
                    DistributionMode.PRIORITY);
            config.filterMode = parseEnum(channelTag.getString(KEY_FILTER_MODE), FilterMode.values(),
                    FilterMode.MATCH_ANY);
            config.priority = Math.max(-99, Math.min(99, channelTag.getInt(KEY_PRIORITY)));
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

                ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound(KEY_ITEM));
                filters[channel][slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
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

                ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound(KEY_ITEM));
                upgrades[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            }
        }

        NodeClipboardConfig config = new NodeClipboardConfig(channels, filters, upgrades, networkId, networkName);
        if (root.contains(KEY_VISIBLE, Tag.TAG_BYTE)) {
            config.renderVisible = root.getBoolean(KEY_VISIBLE);
        }
        return config.isStructurallyValid() ? config : null;
    }

    public PasteResult applyToNode(ServerPlayer player, LogisticsNodeEntity node, ItemStack protectedStack) {
        if (player == null || node == null || channels.length != LogisticsNodeEntity.CHANNEL_COUNT) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!isStructurallyValid()) {
            return PasteResult.CLIPBOARD_INVALID;
        }

        if (!hasCompatibleStructure(node)) {
            return PasteResult.INCOMPATIBLE_TARGET;
        }

        Inventory inventory = player.getInventory();
        int protectedSlot = findProtectedSlot(inventory, protectedStack);
        List<Requirement> requirements = buildRequirements(node);
        List<ItemStack> returnedItems = collectReturnedItems(node);

        if (!hasInventoryRequirements(inventory, requirements, protectedSlot)) {
            return PasteResult.MISSING_ITEMS;
        }
        if (!canFitReturnedItemsAfterConsumption(inventory, requirements, returnedItems, protectedSlot)) {
            return PasteResult.INVENTORY_FULL;
        }

        consumeInventoryRequirements(inventory, requirements, protectedSlot);
        applyToNode(node);
        applyNetworkToNode(node);
        List<ItemStack> leftovers = returnItemsToInventory(inventory, returnedItems, protectedSlot);
        for (ItemStack leftover : leftovers) {
            player.drop(leftover, false);
        }
        inventory.setChanged();

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
            if (shouldReplaceSlot(expected, current)) {
                returnedItems.add(current.copy());
            }
        }

        for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
            ChannelData channelData = node.getChannel(channel);
            if (channelData == null) {
                continue;
            }
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack expected = filterItems[channel][slot];
                ItemStack current = channelData.getFilterItem(slot);
                if (shouldReplaceSlot(expected, current)) {
                    returnedItems.add(current.copy());
                }
            }
        }

        return returnedItems;
    }

    private static boolean shouldReplaceSlot(ItemStack expected, ItemStack current) {
        if (current.isEmpty()) {
            return false;
        }
        return expected.isEmpty() || !ItemStack.isSameItemSameComponents(expected, current);
    }

    private void applyNetworkToNode(LogisticsNodeEntity node) {
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

        LogisticsNetwork targetNetwork = resolveTargetNetwork(registry);
        if (targetNetwork == null) {
            return;
        }

        UUID targetNetworkId = targetNetwork.getId();
        if (currentNetworkId != null && !currentNetworkId.equals(targetNetworkId)) {
            registry.removeNodeFromNetwork(currentNetworkId, node.getUUID());
        }

        node.setNetworkId(targetNetworkId);
        node.setNetworkName(targetNetwork.getName());
        registry.addNodeToNetwork(targetNetworkId, node.getUUID());
    }

    @Nullable
    private LogisticsNetwork resolveTargetNetwork(NetworkRegistry registry) {
        if (networkId != null) {
            LogisticsNetwork byId = registry.getNetwork(networkId);
            if (byId != null) {
                return byId;
            }
        }

        if (networkName != null && !networkName.isBlank()) {
            for (LogisticsNetwork candidate : registry.getAllNetworks().values()) {
                if (networkName.equals(candidate.getName())) {
                    return candidate;
                }
            }
            return registry.createNetwork(networkName);
        }

        if (networkId != null) {
            return registry.createNetwork("Network-" + networkId.toString().substring(0, 6));
        }

        return null;
    }

    private List<Requirement> buildRequirements(LogisticsNodeEntity node) {
        List<Requirement> requirements = new ArrayList<>();

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack required = upgradeItems[slot];
            if (required.isEmpty()) {
                continue;
            }

            if (node != null && ItemStack.isSameItemSameComponents(required, node.getUpgradeItem(slot))) {
                continue;
            }
            addRequirement(requirements, required);
        }

        for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
            ChannelData channelData = node == null ? null : node.getChannel(channel);
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack required = filterItems[channel][slot];
                if (required.isEmpty()) {
                    continue;
                }

                if (channelData != null
                        && ItemStack.isSameItemSameComponents(required, channelData.getFilterItem(slot))) {
                    continue;
                }
                addRequirement(requirements, required);
            }
        }

        return requirements;
    }

    private static void addRequirement(List<Requirement> requirements, ItemStack stack) {
        for (int i = 0; i < requirements.size(); i++) {
            Requirement requirement = requirements.get(i);
            if (ItemStack.isSameItemSameComponents(requirement.stack(), stack)) {
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

    private static boolean hasInventoryRequirements(Inventory inventory, List<Requirement> requirements,
            int protectedSlot) {
        for (Requirement requirement : requirements) {
            int total = countMatching(inventory, requirement.stack(), protectedSlot);
            if (total < requirement.count()) {
                return false;
            }
        }
        return true;
    }

    private static int countMatching(Inventory inventory, ItemStack pattern, int protectedSlot) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stack, pattern)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeInventoryRequirements(Inventory inventory, List<Requirement> requirements,
            int protectedSlot) {
        for (Requirement requirement : requirements) {
            int remaining = requirement.count();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                if (slot == protectedSlot) {
                    continue;
                }
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(stack, requirement.stack())) {
                    continue;
                }

                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                if (stack.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else {
                    inventory.setItem(slot, stack);
                }
                remaining -= consumed;
            }
        }
    }

    private static boolean canFitReturnedItemsAfterConsumption(Inventory inventory, List<Requirement> requirements,
            List<ItemStack> returnedItems, int protectedSlot) {
        ItemStack[] snapshot = copyInventorySlots(inventory);
        if (!consumeRequirementsFromSnapshot(snapshot, requirements, protectedSlot)) {
            return false;
        }
        return insertStacksIntoSnapshot(snapshot, returnedItems, protectedSlot);
    }

    private static ItemStack[] copyInventorySlots(Inventory inventory) {
        ItemStack[] snapshot = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            snapshot[slot] = inventory.getItem(slot).copy();
        }
        return snapshot;
    }

    private static boolean consumeRequirementsFromSnapshot(ItemStack[] slots, List<Requirement> requirements,
            int protectedSlot) {
        for (Requirement requirement : requirements) {
            int remaining = requirement.count();
            for (int slot = 0; slot < slots.length && remaining > 0; slot++) {
                if (slot == protectedSlot) {
                    continue;
                }
                ItemStack stack = slots[slot];
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, requirement.stack())) {
                    continue;
                }
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertStacksIntoSnapshot(ItemStack[] slots, List<ItemStack> stacks, int protectedSlot) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = stack.copy();
            if (!tryInsertIntoSnapshot(slots, remaining, protectedSlot)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tryInsertIntoSnapshot(ItemStack[] slots, ItemStack remaining, int protectedSlot) {
        for (int slot = 0; slot < slots.length && !remaining.isEmpty(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            ItemStack current = slots[slot];
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
            remaining.shrink(move);
        }

        for (int slot = 0; slot < slots.length && !remaining.isEmpty(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            if (!slots[slot].isEmpty()) {
                continue;
            }
            int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            slots[slot] = remaining.copyWithCount(move);
            remaining.shrink(move);
        }

        return remaining.isEmpty();
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
            channel.setPriority(config.priority);

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

        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack expected = upgradeItems[slot];
            ItemStack current = node.getUpgradeItem(slot);

            if (expected.isEmpty()) {
                node.setUpgradeItem(slot, ItemStack.EMPTY);
            } else if (!ItemStack.isSameItemSameComponents(expected, current)) {
                node.setUpgradeItem(slot, expected.copyWithCount(1));
            }
        }

        node.setRenderVisible(renderVisible);
    }

    private static ChannelConfig defaultChannelConfig() {
        ChannelConfig config = new ChannelConfig();
        config.enabled = false;
        config.mode = ChannelMode.IMPORT;
        config.type = ChannelType.ITEM;
        config.batchSize = 8;
        config.tickDelay = 20;
        config.ioDirection = Direction.UP;
        config.redstoneMode = RedstoneMode.ALWAYS_ON;
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
