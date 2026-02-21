package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.model.NodeModel;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;

public class LogisticsNodeRenderer extends EntityRenderer<LogisticsNodeEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "textures/entity/node.png");
    private final NodeModel<LogisticsNodeEntity> model;

    public LogisticsNodeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new NodeModel<>(context.bakeLayer(NodeModel.LAYER_LOCATION));
    }

    @Override
    public void render(LogisticsNodeEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        boolean isHoldingWrench = mc.player.isHolding(Registration.WRENCH.get());
        boolean isVisible = entity.isRenderVisible();

        if (isVisible || isHoldingWrench) {
            renderModel(entity, poseStack, buffer, light, isVisible);
        }

        if (isHoldingWrench) {
            renderWrenchOverlay(entity, poseStack, buffer, light);
        }

        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    protected boolean shouldShowName(LogisticsNodeEntity entity) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isHolding(Registration.WRENCH.get());
    }

    @Override
    protected void renderNameTag(LogisticsNodeEntity entity, Component displayName, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, float partialTick) {
        String networkName = entity.getNetworkName();
        String label = (networkName == null || networkName.isBlank()) ? "No Network" : networkName;
        super.renderNameTag(entity, Component.literal(label), poseStack, buffer, packedLight, partialTick);
    }

    private void renderModel(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer, int light,
            boolean isVisible) {
        poseStack.pushPose();

        float scaleXZ = 17.0f / 16.0f;
        float scaleY = 18.0f / 16.0f;
        poseStack.scale(-scaleXZ, -scaleY, scaleXZ);
        poseStack.translate(0.0, -1.0625, 0.0);

        int color = isVisible ? -1 : 0x55FFFFFF; // Semi transparent

        VertexConsumer consumer = buffer.getBuffer(model.renderType(getTextureLocation(entity)));
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);

        poseStack.popPose();
    }

    private void renderWrenchOverlay(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        renderHighlightBox(poseStack, buffer);

        if (Config.debugMode) {
            poseStack.pushPose();
            poseStack.translate(0.0, -0.75, 0.0);
            renderDebugInfo(entity, poseStack, buffer, light);
            poseStack.popPose();
        }
    }

    private void renderDebugInfo(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, -0.25, 0.0);

        String nodeId = "Node: " + entity.getUUID().toString().substring(0, 8);
        renderLabel(entity, nodeId, poseStack, buffer, light);

        poseStack.translate(0.0, -0.25, 0.0);

        StringBuilder channels = new StringBuilder("Ch: ");
        for (int i = 0; i < entity.getChannels().length; i++) {
            var channel = entity.getChannel(i);
            if (channel != null && channel.isEnabled()) {
                channels.append(i).append(" ");
            }
        }
        renderLabel(entity, channels.toString(), poseStack, buffer, light);

        poseStack.popPose();
    }

    private void renderHighlightBox(PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer builder = buffer.getBuffer(ModRenderTypes.OVERLAY);
        Matrix4f matrix = poseStack.last().pose();

        float min = -0.501f;
        float max = 0.501f;
        float r = 0f, g = 1f, b = 0f, a = 0.35f;

        // Top
        builder.addVertex(matrix, min, max, min).setColor(r, g, b, a);
        builder.addVertex(matrix, min, max, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, max, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, max, min).setColor(r, g, b, a);

        // Bottom
        builder.addVertex(matrix, max, min, min).setColor(r, g, b, a);
        builder.addVertex(matrix, max, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, min).setColor(r, g, b, a);

        // West
        builder.addVertex(matrix, min, max, min).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, min).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, min, max, max).setColor(r, g, b, a);

        // East
        builder.addVertex(matrix, max, max, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, min, min).setColor(r, g, b, a);
        builder.addVertex(matrix, max, max, min).setColor(r, g, b, a);

        // North
        builder.addVertex(matrix, max, max, min).setColor(r, g, b, a);
        builder.addVertex(matrix, max, min, min).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, min).setColor(r, g, b, a);
        builder.addVertex(matrix, min, max, min).setColor(r, g, b, a);

        // South
        builder.addVertex(matrix, min, max, max).setColor(r, g, b, a);
        builder.addVertex(matrix, min, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, min, max).setColor(r, g, b, a);
        builder.addVertex(matrix, max, max, max).setColor(r, g, b, a);
    }

    private void renderLabel(LogisticsNodeEntity entity, String text, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        var font = this.getFont();
        float x = (float) (-font.width(text) / 2);
        int fullbright = 15728880;

        // Two-pass rendering like vanilla nametags: SEE_THROUGH for visibility behind geometry, NORMAL for solid text
        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0x40000000, fullbright);
        font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, fullbright);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LogisticsNodeEntity entity) {
        return TEXTURE;
    }
}
