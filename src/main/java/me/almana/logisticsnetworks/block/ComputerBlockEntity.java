package me.almana.logisticsnetworks.block;
import com.mojang.serialization.MapCodec;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class ComputerBlockEntity extends BlockEntity {

    private static final MapCodec<List<UUID>> STARRED_CODEC = NeoForgeExtraCodecs.mapWithAlternative(
            ComponentCodecs.lenientList(UUIDUtil.CODEC).fieldOf("starred_networks"),
            ComponentCodecs.lenientList(UUIDUtil.STRING_CODEC).lenientOptionalFieldOf("StarredNetworks", List.of()));
    private static final MapCodec<Optional<UUID>> OWNER_CODEC = UUIDUtil.LENIENT_CODEC.lenientOptionalFieldOf("owner");

    private final Set<UUID> starredNetworks = new LinkedHashSet<>();
    @Nullable
    private UUID owner;

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

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.store(STARRED_CODEC, List.copyOf(starredNetworks));
        tag.store(OWNER_CODEC, Optional.ofNullable(owner));
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        starredNetworks.clear();
        tag.read(STARRED_CODEC).ifPresent(starredNetworks::addAll);
        owner = tag.read(OWNER_CODEC).flatMap(Function.identity()).orElse(null);
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
