package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
        if (starredNetworks.contains(networkId)) {
            starredNetworks.remove(networkId);
        } else {
            starredNetworks.add(networkId);
        }
        markUpdated();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag starredList = new ListTag();
        for (UUID networkId : starredNetworks) {
            starredList.add(StringTag.valueOf(networkId.toString()));
        }
        tag.put(TAG_STARRED_NETWORKS, starredList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        starredNetworks.clear();
        ListTag starredList = tag.getList(TAG_STARRED_NETWORKS, Tag.TAG_STRING);
        for (int i = 0; i < starredList.size(); i++) {
            try {
                starredNetworks.add(UUID.fromString(starredList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
