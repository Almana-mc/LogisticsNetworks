package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LabelUpgradeTemplate {

    static final Codec<LabelUpgradeTemplate> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.lenientOptionalFieldOf("revision", 0L).forGetter(LabelUpgradeTemplate::revision),
            UUIDUtil.CODEC.fieldOf("player").forGetter(LabelUpgradeTemplate::playerId),
            StorageLink.CODEC.lenientOptionalFieldOf("storage_link").forGetter(LabelUpgradeTemplate::optionalLink),
            SlotStack.LIST_CODEC.lenientOptionalFieldOf("upgrades", List.of())
                    .forGetter(LabelUpgradeTemplate::upgradeSlots),
            ChannelData.LIST_CODEC.lenientOptionalFieldOf("channels", List.of())
                    .forGetter(template -> template.channels)
    ).apply(instance, LabelUpgradeTemplate::decoded));
    static final Codec<LabelUpgradeTemplate> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("Revision").forGetter(LabelUpgradeTemplate::revision),
            UUIDUtil.LENIENT_CODEC.fieldOf("Player").forGetter(LabelUpgradeTemplate::playerId),
            StorageLink.CODEC.lenientOptionalFieldOf("StorageLink").forGetter(LabelUpgradeTemplate::optionalLink),
            GlobalPos.CODEC.lenientOptionalFieldOf("MELink").forGetter(template -> Optional.empty()),
            SlotStack.LEGACY_LIST_CODEC.lenientOptionalFieldOf("Upgrades", List.of())
                    .forGetter(LabelUpgradeTemplate::upgradeSlots),
            ChannelData.LIST_CODEC.lenientOptionalFieldOf("Channels", List.of())
                    .forGetter(template -> template.channels)
    ).apply(instance, (revision, player, link, meLink, upgrades, channels) -> decoded(revision, player,
            link.or(() -> meLink.map(position -> new StorageLink(StorageBackend.AE2, position))),
            upgrades, channels)));
    public static final Codec<LabelUpgradeTemplate> CODEC = Codec.withAlternative(CURRENT_CODEC, LEGACY_CODEC);

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

    private static LabelUpgradeTemplate decoded(long revision, UUID player, Optional<StorageLink> link,
                                                List<SlotStack> upgrades, List<ChannelData> channels) {
        return new LabelUpgradeTemplate(revision, player, link.orElse(null),
                Arrays.asList(SlotStack.toSlots(upgrades, LogisticsNodeEntity.UPGRADE_SLOT_COUNT)), channels);
    }

    private Optional<StorageLink> optionalLink() {
        return Optional.ofNullable(storageLink);
    }

    private List<SlotStack> upgradeSlots() {
        return SlotStack.nonEmpty(upgrades);
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
