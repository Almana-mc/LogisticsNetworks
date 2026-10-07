package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ServerRackBlockEntity extends BlockEntity {

    private static final String KEY_CONFIG = "config";

    private ServerRackConfig config = ServerRackConfig.EMPTY;

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(me.almana.logisticsnetworks.registration.Registration.SERVER_RACK_BLOCK_ENTITY.get(), pos, state);
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(KEY_CONFIG, ServerRackConfig.CODEC, config);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        config = input.read(KEY_CONFIG, ServerRackConfig.CODEC).orElse(ServerRackConfig.EMPTY);
    }
}
