package me.almana.logisticsnetworks.block;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class ComputerBlockEntity extends BlockEntity {

    private static final Codec<List<UUID>> STARRED_CODEC = Codec.withAlternative(
            ComponentCodecs.lenientList(UUIDUtil.CODEC).fieldOf("starred_networks").codec(),
            ComponentCodecs.lenientList(UUIDUtil.STRING_CODEC)
                    .lenientOptionalFieldOf("StarredNetworks", List.of()).codec());
    private static final Codec<Optional<UUID>> OWNER_CODEC = UUIDUtil.CODEC.lenientOptionalFieldOf("owner").codec();

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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.merge((CompoundTag) ComponentCodecs.encode(STARRED_CODEC, registries, List.copyOf(starredNetworks)));
        tag.merge((CompoundTag) ComponentCodecs.encode(OWNER_CODEC, registries, Optional.ofNullable(owner)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        starredNetworks.clear();
        ComponentCodecs.parse(STARRED_CODEC, registries, tag).ifPresent(starredNetworks::addAll);
        owner = ComponentCodecs.parse(OWNER_CODEC, registries, tag).flatMap(Function.identity()).orElse(null);
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
