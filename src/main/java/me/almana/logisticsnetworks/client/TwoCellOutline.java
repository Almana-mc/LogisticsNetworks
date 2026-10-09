package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.block.TwoCellBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import java.util.List;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class TwoCellOutline {

    @SubscribeEvent
    public static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        BlockState state = event.getBlockState();
        if (!(state.getBlock() instanceof TwoCellBlock block)) return;
        ClientLevel level = event.getLevel();
        BlockPos pos = event.getBlockPos();
        BlockPos other = block.partnerPos(state, pos);
        BlockState otherState = level.getBlockState(other);
        if (!block.isPartner(state, otherState)) return;

        CollisionContext context = event.getCollisionContext();
        VoxelShape shape = Shapes.or(state.getShape(level, pos, context),
                otherState.getShape(level, other, context).move(other.subtract(pos)));
        event.getLevelRenderState().blockOutlineRenderState = new BlockOutlineRenderState(pos,
                event.isInTranslucentPass(), event.isHighContrast(), shape, List.of());
        // Cancel keeps our state
        event.setCanceled(true);
    }

    private TwoCellOutline() {
    }
}
