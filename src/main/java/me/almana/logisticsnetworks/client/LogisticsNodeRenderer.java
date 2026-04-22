package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.model.NodeModel;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogisticsNodeRenderer extends EntityRenderer<LogisticsNodeEntity> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "textures/entity/node.png");
    private final NodeModel<LogisticsNodeEntity> model;

    private static final List<LogisticsNodeEntity> cachedNodeList = new ArrayList<>();
    private static Set<Integer> allowedNodeIds;
    private static Set<Integer> visibleNodeIds;
    private static long lastComputeTick = -1;

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

        boolean isVisible = entity.isRenderVisible();
        boolean isHighlighted = entity.isHighlighted();
        boolean isHoldingWrench = mc.player.isHolding(Registration.WRENCH.get());

        if (!isVisible && !isHighlighted && !isHoldingWrench)
            return;

        updateAllowedNodes(mc);

        if (isHoldingWrench && allowedNodeIds != null && !allowedNodeIds.contains(entity.getId())) {
            isHoldingWrench = false;
            if (!isVisible && !isHighlighted)
                return;
        }

        boolean canRenderModel = visibleNodeIds == null || visibleNodeIds.contains(entity.getId());
        if ((isVisible || isHoldingWrench || isHighlighted) && canRenderModel) {
            renderModel(entity, poseStack, buffer, light, isVisible);
        }

        if (isHighlighted) {
            renderHighlightBox(poseStack, buffer, 0.15f, 0.45f, 1.0f, 0.35f, true);
        } else if (isHoldingWrench) {
            renderWrenchOverlay(entity, poseStack, buffer, light);
        }

        if (isHoldingWrench || isHighlighted) {
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }
    }

    @Override
    protected boolean shouldShowName(LogisticsNodeEntity entity) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isHolding(Registration.WRENCH.get())
                && (allowedNodeIds == null || allowedNodeIds.contains(entity.getId()));
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
        poseStack.translate(0.0, -17.0f / 16.0f - (8.0f / 18.0f), 0.0);

        int color = isVisible ? -1 : 0x55FFFFFF;

        VertexConsumer consumer = buffer
                .getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);

        poseStack.popPose();
    }

    private void renderWrenchOverlay(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        renderHighlightBox(poseStack, buffer, 0f, 1f, 0f, 0.35f, false);

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

    private void renderHighlightBox(PoseStack poseStack, MultiBufferSource buffer, float r, float g, float b,
            float a, boolean xray) {
        VertexConsumer builder = buffer.getBuffer(xray ? ModRenderTypes.OVERLAY_XRAY : ModRenderTypes.OVERLAY);
        Matrix4f matrix = poseStack.last().pose();

        float minX = -0.501f, maxX = 0.501f;
        float minY = -0.001f, maxY = 1.001f;
        float minZ = -0.501f, maxZ = 0.501f;

        // Top
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        // Bottom
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);

        // West
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);

        // East
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        // North
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        // South
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
    }

    private void renderLabel(LogisticsNodeEntity entity, String text, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        var font = this.getFont();
        float x = (float) (-font.width(text) / 2);
        int fullbright = 15728880;

        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0x40000000, fullbright);
        font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, fullbright);

        poseStack.popPose();
    }

    private static void updateAllowedNodes(Minecraft mc) {
        long tick = mc.level.getGameTime();
        if (tick - lastComputeTick < 5)
            return;
        lastComputeTick = tick;

        cachedNodeList.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LogisticsNodeEntity node) {
                cachedNodeList.add(node);
            }
        }

        int wrenchLimit = ClientConfig.maxRenderedNodes;
        int visibleLimit = ClientConfig.maxVisibleNodes;
        boolean needsSort = cachedNodeList.size() > wrenchLimit
                || (visibleLimit > 0 && cachedNodeList.size() > visibleLimit);

        if (!needsSort) {
            allowedNodeIds = null;
            visibleNodeIds = null;
            return;
        }

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        cachedNodeList.sort(Comparator.comparingDouble(n -> n.distanceToSqr(px, py, pz)));

        if (cachedNodeList.size() > wrenchLimit) {
            if (allowedNodeIds == null) {
                allowedNodeIds = new HashSet<>(wrenchLimit * 2);
            } else {
                allowedNodeIds.clear();
            }
            for (int i = 0; i < wrenchLimit && i < cachedNodeList.size(); i++) {
                allowedNodeIds.add(cachedNodeList.get(i).getId());
            }
        } else {
            allowedNodeIds = null;
        }

        if (visibleLimit > 0 && cachedNodeList.size() > visibleLimit) {
            if (visibleNodeIds == null) {
                visibleNodeIds = new HashSet<>(visibleLimit * 2);
            } else {
                visibleNodeIds.clear();
            }
            for (int i = 0; i < visibleLimit && i < cachedNodeList.size(); i++) {
                visibleNodeIds.add(cachedNodeList.get(i).getId());
            }
        } else {
            visibleNodeIds = null;
        }
    }

    @Override
    protected int getBlockLightLevel(LogisticsNodeEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public Identifier getTextureLocation(LogisticsNodeEntity entity) {
        return TEXTURE;
    }
}
