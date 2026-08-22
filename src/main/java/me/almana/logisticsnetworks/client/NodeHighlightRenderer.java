package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.integration.create.NodeRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class NodeHighlightRenderer {
    private static final float MIN_XZ = -0.501F;
    private static final float MAX_XZ = 0.501F;
    private static final float MIN_Y = -0.001F;
    private static final float MAX_Y = 1.001F;

    private NodeHighlightRenderer() {
    }

    static void queue(NodeRenderContext context, float red, float green, float blue, float alpha, boolean xray) {
        NodeHighlightQueue.add(context.position(), context.rotation(), red, green, blue, alpha, xray);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        var requests = NodeHighlightQueue.drain(event.getStage());
        if (requests.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();

        for (var request : requests) {
            poseStack.pushPose();
            Vec3 offset = request.position().subtract(cameraPosition);
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(request.rotation());
            renderBox(poseStack.last().pose(), bufferSource, request);
            poseStack.popPose();
        }

        bufferSource.endBatch(ModRenderTypes.OVERLAY);
        bufferSource.endBatch(ModRenderTypes.OVERLAY_XRAY);
    }

    private static void renderBox(Matrix4f matrix, MultiBufferSource buffer,
            NodeHighlightQueue.HighlightRequest request) {
        VertexConsumer builder = buffer.getBuffer(request.xray()
                ? ModRenderTypes.OVERLAY_XRAY
                : ModRenderTypes.OVERLAY);
        float red = request.red();
        float green = request.green();
        float blue = request.blue();
        float alpha = request.alpha();

        builder.addVertex(matrix, MIN_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);

        builder.addVertex(matrix, MAX_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);

        builder.addVertex(matrix, MIN_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);

        builder.addVertex(matrix, MAX_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);

        builder.addVertex(matrix, MAX_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MIN_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MAX_Y, MIN_XZ).setColor(red, green, blue, alpha);

        builder.addVertex(matrix, MIN_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MIN_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MIN_Y, MAX_XZ).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, MAX_XZ, MAX_Y, MAX_XZ).setColor(red, green, blue, alpha);
    }
}
