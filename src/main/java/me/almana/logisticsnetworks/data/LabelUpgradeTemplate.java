package me.almana.logisticsnetworks.data;

import com.mojang.serialization.DataResult;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LabelUpgradeTemplate {

    private static final String KEY_REVISION = "Revision";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_STORAGE_LINK = "StorageLink";
    private static final String KEY_LEGACY_ME_LINK = "MELink";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_ITEM = "Item";
    private final long revision;
    private final UUID playerId;
    @Nullable
    private final StorageLink storageLink;
    private final List<ItemStack> upgrades;
    private final List<ChannelData> channels;

    public LabelUpgradeTemplate(long revision, UUID playerId, @Nullable StorageLink storageLink,
                                List<ItemStack> upgrades, List<ChannelData> channels) {
        this.revision = revision;
        this.playerId = playerId;
        this.storageLink = storageLink;
        this.upgrades = copyStacks(upgrades);
        this.channels = copyChannels(channels);
    }

    public long revision() {
        return revision;
    }

    public UUID playerId() {
        return playerId;
    }

    @Nullable
    public StorageLink storageLink() {
        return storageLink;
    }

    public List<ItemStack> upgrades() {
        return copyStacks(upgrades);
    }

    public List<ChannelData> channels() {
        return copyChannels(channels);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_REVISION, revision);
        tag.putUUID(KEY_PLAYER, playerId);
        if (storageLink != null) {
            DataResult<Tag> encoded = StorageLink.CODEC.encodeStart(NbtOps.INSTANCE, storageLink);
            encoded.result().ifPresent(value -> tag.put(KEY_STORAGE_LINK, value));
        }
        ListTag upgradesTag = new ListTag();
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack stack = upgrades.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SLOT, slot);
            entry.put(KEY_ITEM, stack.save(provider));
            upgradesTag.add(entry);
        }
        tag.put(KEY_UPGRADES, upgradesTag);
        ListTag channelsTag = new ListTag();
        for (ChannelData channel : channels) channelsTag.add(channel.save(provider));
        tag.put(KEY_CHANNELS, channelsTag);
        return tag;
    }

    @Nullable
    public static LabelUpgradeTemplate load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains(KEY_PLAYER) || !tag.contains(KEY_REVISION)) return null;
        StorageLink link = null;
        if (tag.contains(KEY_STORAGE_LINK)) {
            link = StorageLink.CODEC.parse(NbtOps.INSTANCE, tag.get(KEY_STORAGE_LINK)).result().orElse(null);
        } else if (tag.contains(KEY_LEGACY_ME_LINK)) {
            GlobalPos legacy = GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(KEY_LEGACY_ME_LINK))
                    .result().orElse(null);
            if (legacy != null) link = new StorageLink(StorageBackend.AE2, legacy);
        }
        List<ItemStack> upgrades = new ArrayList<>();
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            upgrades.add(ItemStack.EMPTY);
        }
        ListTag upgradesTag = tag.getList(KEY_UPGRADES, Tag.TAG_COMPOUND);
        for (Tag value : upgradesTag) {
            if (!(value instanceof CompoundTag entry)) continue;
            int slot = entry.getInt(KEY_SLOT);
            if (slot >= 0 && slot < upgrades.size()) {
                upgrades.set(slot, ItemStack.parseOptional(provider, entry.getCompound(KEY_ITEM)));
            }
        }
        List<ChannelData> channels = new ArrayList<>();
        ListTag channelsTag = tag.getList(KEY_CHANNELS, Tag.TAG_COMPOUND);
        for (Tag value : channelsTag) {
            if (!(value instanceof CompoundTag channelTag)) continue;
            ChannelData channel = new ChannelData();
            channel.load(channelTag, provider);
            channels.add(channel);
        }
        return new LabelUpgradeTemplate(tag.getLong(KEY_REVISION), tag.getUUID(KEY_PLAYER),
                link, upgrades, channels);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
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
