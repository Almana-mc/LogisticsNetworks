package me.almana.logisticsnetworks.block;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class ComputerBlockEntity extends BlockEntity {

    private static final String TAG_STARRED_NETWORKS = "StarredNetworks";

    private final Set<UUID> starredNetworks = new LinkedHashSet<>();

    public ComputerBlockEntity(BlockPos pos, BlockState blockState) {
        super(me.almana.logisticsnetworks.registration.Registration.computerBlockEntityType(), pos, blockState);
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
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        var starredList = tag.list(TAG_STARRED_NETWORKS, Codec.STRING);
        for (UUID networkId : starredNetworks) {
            starredList.add(networkId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        starredNetworks.clear();
        for (String rawId : tag.listOrEmpty(TAG_STARRED_NETWORKS, Codec.STRING)) {
            try {
                starredNetworks.add(UUID.fromString(rawId));
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
