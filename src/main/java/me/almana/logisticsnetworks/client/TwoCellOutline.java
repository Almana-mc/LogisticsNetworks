package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.block.TwoCellBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public final class TwoCellOutline {

    @SubscribeEvent
    public static void onTwoCellHighlight(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TwoCellBlock block)) return;
        BlockPos other = block.partnerPos(state, pos);
        BlockState otherState = level.getBlockState(other);
        if (!block.isPartner(state, otherState)) return;

        CollisionContext context = CollisionContext.of(event.getCamera().getEntity());
        BlockPos offset = other.subtract(pos);
        VoxelShape shape = Shapes.or(state.getShape(level, pos, context),
                otherState.getShape(level, other, context).move(offset.getX(), offset.getY(), offset.getZ()));
        Vec3 camera = event.getCamera().getPosition();
        draw(event.getPoseStack().last(), event.getMultiBufferSource().getBuffer(RenderType.lines()), shape,
                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        event.setCanceled(true);
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer lines, VoxelShape shape, double x, double y, double z) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            lines.vertex(pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .color(0f, 0f, 0f, 0.4f).normal(pose.normal(), dx, dy, dz).endVertex();
            lines.vertex(pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .color(0f, 0f, 0f, 0.4f).normal(pose.normal(), dx, dy, dz).endVertex();
        });
    }

    private TwoCellOutline() {
    }
}
