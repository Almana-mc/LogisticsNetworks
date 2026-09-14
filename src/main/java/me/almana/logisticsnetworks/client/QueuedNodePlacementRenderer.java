package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.network.SyncQueuedNodePlacementPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class QueuedNodePlacementRenderer {

    private static final Set<Target> TARGETS = new HashSet<>();
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private QueuedNodePlacementRenderer() {
    }

    public static void update(SyncQueuedNodePlacementPayload payload) {
        Target target = new Target(payload.dimension(), payload.pos().immutable());
        if (payload.queued()) TARGETS.add(target);
        else TARGETS.remove(target);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (TARGETS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Identifier dimension = minecraft.level.dimension().identifier();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lines());
        PoseStack poseStack = event.getPoseStack();
        float pulse = 0.7F + 0.3F * (float) Math.sin(System.currentTimeMillis() / 180.0D);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (Target target : TARGETS) {
            if (!target.dimension().equals(dimension)) continue;
            AABB box = new AABB(target.pos()).inflate(0.008D);
            if (box.getCenter().distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) continue;
            ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(box),
                    0.0D, 0.0D, 0.0D, 0xFFFFA31A, pulse);
        }
        poseStack.popPose();
        buffers.endBatch(RenderTypes.lines());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TARGETS.clear();
    }

    private record Target(Identifier dimension, BlockPos pos) {
    }
}
