package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.util.ItemStackCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LabelUpgradeTemplate {

    private static final String KEY_REVISION = "Revision";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_ITEM = "Item";

    private final long revision;
    private final UUID playerId;
    private final List<ItemStack> upgrades;
    private final List<ChannelData> channels;

    public LabelUpgradeTemplate(long revision, UUID playerId, List<ItemStack> upgrades, List<ChannelData> channels) {
        this.revision = revision;
        this.playerId = playerId;
        this.upgrades = copyStacks(upgrades);
        this.channels = copyChannels(channels);
    }

    public long revision() {
        return revision;
    }

    public UUID playerId() {
        return playerId;
    }

    public List<ItemStack> upgrades() {
        return copyStacks(upgrades);
    }

    public List<ChannelData> channels() {
        return copyChannels(channels);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_REVISION, revision);
        tag.putUUID(KEY_PLAYER, playerId);
        ListTag upgradesTag = new ListTag();
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack stack = upgrades.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SLOT, slot);
            entry.put(KEY_ITEM, ItemStackCompat.save(stack, null));
            upgradesTag.add(entry);
        }
        tag.put(KEY_UPGRADES, upgradesTag);
        ListTag channelsTag = new ListTag();
        for (ChannelData channel : channels) channelsTag.add(channel.save(RegistryAccess.EMPTY));
        tag.put(KEY_CHANNELS, channelsTag);
        return tag;
    }

    @Nullable
    public static LabelUpgradeTemplate load(CompoundTag tag) {
        if (!tag.hasUUID(KEY_PLAYER) || !tag.contains(KEY_REVISION)) return null;
        List<ItemStack> upgrades = new ArrayList<>();
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            upgrades.add(ItemStack.EMPTY);
        }
        for (Tag value : tag.getList(KEY_UPGRADES, Tag.TAG_COMPOUND)) {
            if (!(value instanceof CompoundTag entry)) continue;
            int slot = entry.getInt(KEY_SLOT);
            if (slot >= 0 && slot < upgrades.size()) {
                upgrades.set(slot, ItemStackCompat.parseOptional(null, entry.getCompound(KEY_ITEM)));
            }
        }
        List<ChannelData> channels = new ArrayList<>();
        for (Tag value : tag.getList(KEY_CHANNELS, Tag.TAG_COMPOUND)) {
            if (!(value instanceof CompoundTag channelTag)) continue;
            ChannelData channel = new ChannelData();
            channel.load(channelTag, RegistryAccess.EMPTY);
            channels.add(channel);
        }
        return new LabelUpgradeTemplate(tag.getLong(KEY_REVISION), tag.getUUID(KEY_PLAYER), upgrades, channels);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) copy.add(ItemStackCompat.copyWithCount(stack, 1));
        return copy;
    }

    private static List<ChannelData> copyChannels(List<ChannelData> source) {
        List<ChannelData> copy = new ArrayList<>(source.size());
        for (ChannelData channel : source) {
            ChannelData clone = new ChannelData();
            clone.copyFrom(channel);
            copy.add(clone);
        }
        return copy;
    }
}
