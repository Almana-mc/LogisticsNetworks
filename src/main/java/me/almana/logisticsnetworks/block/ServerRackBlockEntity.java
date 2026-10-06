package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ServerRackBlockEntity extends BlockEntity {

    private static final String KEY_CONFIG = "config";

    private ServerRackConfig config = ServerRackConfig.EMPTY;

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SERVER_RACK_BLOCK_ENTITY.get(), pos, state);
    }

    public ServerRackConfig getConfig() {
        return config;
    }

    public void setConfig(ServerRackConfig config) {
        this.config = config;
        setChanged();
        register();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    // Also runs on chunk unload
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            NetworkRegistry.get(serverLevel).removeRack(GlobalPos.of(level.dimension(), worldPosition));
        }
    }

    private void register() {
        if (level instanceof ServerLevel serverLevel) {
            NetworkRegistry.get(serverLevel).putRack(GlobalPos.of(level.dimension(), worldPosition), config);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_CONFIG, ComponentCodecs.encode(ServerRackConfig.CODEC, registries, config));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        config = tag.contains(KEY_CONFIG)
                ? ComponentCodecs.parse(ServerRackConfig.CODEC, registries, tag.get(KEY_CONFIG))
                        .orElse(ServerRackConfig.EMPTY)
                : ServerRackConfig.EMPTY;
    }
}
