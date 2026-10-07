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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ComputerBlockEntity extends BlockEntity {

    private static final MapCodec<List<UUID>> STARRED_CODEC = NeoForgeExtraCodecs.mapWithAlternative(
            ComponentCodecs.lenientList(UUIDUtil.CODEC).fieldOf("starred_networks"),
            ComponentCodecs.lenientList(UUIDUtil.STRING_CODEC).lenientOptionalFieldOf("StarredNetworks", List.of()));

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
        tag.store(STARRED_CODEC, List.copyOf(starredNetworks));
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        starredNetworks.clear();
        tag.read(STARRED_CODEC).ifPresent(starredNetworks::addAll);
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
