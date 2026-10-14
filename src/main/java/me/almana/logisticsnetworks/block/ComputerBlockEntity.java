package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class ComputerBlockEntity extends BlockEntity {

    private static final String KEY_STARRED = "starred_networks";
    private static final String KEY_OWNER = "owner";

    private final Set<UUID> starredNetworks = new LinkedHashSet<>();
    @Nullable
    private UUID owner;

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

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag starred = new ListTag();
        for (UUID networkId : starredNetworks) {
            starred.add(NbtUtils.createUUID(networkId));
        }
        tag.put(KEY_STARRED, starred);
        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        starredNetworks.clear();
        for (Tag entry : tag.getList(KEY_STARRED, Tag.TAG_INT_ARRAY)) {
            try {
                starredNetworks.add(NbtUtils.loadUUID(entry));
            } catch (IllegalArgumentException ignored) {
            }
        }
        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
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
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
