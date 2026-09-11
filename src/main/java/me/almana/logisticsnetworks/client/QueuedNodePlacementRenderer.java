package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.integration.iris.IrisCompat;
import me.almana.logisticsnetworks.network.SyncQueuedNodePlacementPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        var renderStage = IrisCompat.isLoaded() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (event.getStage() != renderStage || IrisCompat.isRenderingShadowPass() || TARGETS.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ResourceLocation dimension = minecraft.level.dimension().location();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderType renderType = IrisCompat.isLoaded() ? ModRenderTypes.SELECTION_LINES : RenderType.lines();
        PoseStack poseStack = event.getPoseStack();
        float pulse = 0.7F + 0.3F * (float) Math.sin(Util.getMillis() / 180.0D);

        bufferSource.endLastBatch();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        poseStack.pushPose();
        try {
            if (IrisCompat.isLoaded()) {
                minecraft.getMainRenderTarget().bindWrite(false);
                poseStack.mulPose(event.getModelViewMatrix());
            }
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VertexConsumer consumer = bufferSource.getBuffer(renderType);
            for (Target target : TARGETS) {
                if (!target.dimension().equals(dimension)) continue;
                AABB box = new AABB(target.pos()).inflate(0.008D);
                if (box.getCenter().distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) continue;
                LevelRenderer.renderLineBox(poseStack, consumer, box, 1.0F, 0.65F, 0.1F, pulse);
            }
            bufferSource.endBatch(renderType);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TARGETS.clear();
    }

    private record Target(ResourceLocation dimension, BlockPos pos) {
    }
}
