package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class ComputerBlockEntity extends BlockEntity {
    private static final String TAG_STARRED_NETWORKS = "StarredNetworks";
    private final Set<UUID> starredNetworks = new LinkedHashSet<>();

    public ComputerBlockEntity(BlockPos pos, BlockState blockState) {
        super(Registration.COMPUTER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public Set<UUID> getStarredNetworks() {
        return Set.copyOf(starredNetworks);
    }

    public void toggleNetworkStar(UUID networkId) {
        if (!starredNetworks.remove(networkId))
            starredNetworks.add(networkId);
        markUpdated();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (UUID networkId : starredNetworks)
            list.add(StringTag.valueOf(networkId.toString()));
        tag.put(TAG_STARRED_NETWORKS, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        starredNetworks.clear();
        for (Tag value : tag.getList(TAG_STARRED_NETWORKS, Tag.TAG_STRING)) {
            try {
                starredNetworks.add(UUID.fromString(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
