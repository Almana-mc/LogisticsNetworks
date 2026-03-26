package me.almana.logisticsnetworks.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogisticsNodeRenderer extends EntityRenderer<LogisticsNodeEntity, LogisticsNodeRenderState> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "textures/entity/node.png");

    private static Set<Integer> allowedNodeIds;
    private static long lastComputeTick = Long.MIN_VALUE;

    private final NodeModel model;

    public LogisticsNodeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new NodeModel(context.bakeLayer(NodeModel.LAYER_LOCATION));
    }

    @Override
    public LogisticsNodeRenderState createRenderState() {
        return new LogisticsNodeRenderState();
    }

    @Override
    public void extractRenderState(LogisticsNodeEntity entity, LogisticsNodeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.renderVisible = entity.isRenderVisible();
        state.highlighted = entity.isHighlighted();
        state.wrenchVisible = isWrenchVisible(entity);
        state.debugMode = Config.debugMode;
        state.debugNodeId = "";
        state.debugChannels = "";

        if (state.wrenchVisible && state.debugMode) {
            state.debugNodeId = "Node: " + entity.getUUID().toString().substring(0, 8);
            state.debugChannels = buildChannelDebugText(entity);
        }
    }

    @Override
    public void submit(LogisticsNodeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        if (!(state.renderVisible || state.wrenchVisible || state.highlighted)) {
            return;
        }

        submitModel(state, poseStack, submitNodeCollector);

        if (state.highlighted) {
            submitHighlightBox(poseStack, submitNodeCollector, 0.15f, 0.45f, 1.0f, 0.35f, true);
        } else if (state.wrenchVisible) {
            submitHighlightBox(poseStack, submitNodeCollector, 0.0f, 1.0f, 0.0f, 0.35f, false);
            if (state.debugMode) {
                submitDebugLabels(state, poseStack, submitNodeCollector);
            }
        }

        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    protected boolean shouldShowName(LogisticsNodeEntity entity, double distanceToCameraSq) {
        return isWrenchVisible(entity);
    }

    @Override
    protected Component getNameTag(LogisticsNodeEntity entity) {
        String networkName = entity.getNetworkName();
        String label = networkName == null || networkName.isBlank() ? "No Network" : networkName;
        return Component.literal(label);
    }

    @Override
    protected int getBlockLightLevel(LogisticsNodeEntity entity, BlockPos pos) {
        return 15;
    }

    private void submitModel(LogisticsNodeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        poseStack.pushPose();
        float scaleXZ = 17.0f / 16.0f;
        float scaleY = 18.0f / 16.0f;
        poseStack.scale(-scaleXZ, -scaleY, scaleXZ);
        poseStack.translate(0.0, -17.0f / 16.0f - (8.0f / 18.0f), 0.0);

        int color = state.renderVisible ? -1 : 0x55FFFFFF;
        submitNodeCollector.submitModel(
                this.model,
                state,
                poseStack,
                this.model.renderType(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                color,
                null,
                state.outlineColor,
                null);
        poseStack.popPose();
    }

    private void submitHighlightBox(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, float r, float g,
            float b, float a, boolean xray) {
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                xray ? NodeRenderTypes.overlayXray() : NodeRenderTypes.overlay(),
                (pose, buffer) -> addHighlightBox(pose.pose(), buffer, r, g, b, a));
    }

    private void submitDebugLabels(LogisticsNodeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        OrderedSubmitNodeCollector ordered = submitNodeCollector.order(1);
        poseStack.pushPose();
        poseStack.translate(0.0, -1.0, 0.0);
        submitDebugLabel(ordered, poseStack, state.debugNodeId);
        poseStack.translate(0.0, -0.25, 0.0);
        submitDebugLabel(ordered, poseStack, state.debugChannels);
        poseStack.popPose();
    }

    private void submitDebugLabel(OrderedSubmitNodeCollector submitNodeCollector, PoseStack poseStack, String text) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Font font = this.getFont();
        FormattedCharSequence sequence = Component.literal(text).getVisualOrderText();
        float x = -font.width(sequence) / 2.0F;
        int fullbright = 15728880;

        submitNodeCollector.submitText(poseStack, x, 0.0F, sequence, false, Font.DisplayMode.SEE_THROUGH, fullbright,
                0x20FFFFFF, 0x40000000, 0);
        submitNodeCollector.submitText(poseStack, x, 0.0F, sequence, false, Font.DisplayMode.NORMAL, fullbright,
                0xFFFFFFFF, 0, 0);

        poseStack.popPose();
    }

    private static boolean isWrenchVisible(LogisticsNodeEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isHolding(Registration.WRENCH.get())) {
            return false;
        }
        updateAllowedNodes(mc);
        return allowedNodeIds == null || allowedNodeIds.contains(entity.getId());
    }

    private static void updateAllowedNodes(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            allowedNodeIds = null;
            lastComputeTick = Long.MIN_VALUE;
            return;
        }

        long tick = mc.level.getGameTime();
        if (tick == lastComputeTick) {
            return;
        }
        lastComputeTick = tick;

        int limit = ClientConfig.maxRenderedNodes;
        List<LogisticsNodeEntity> nodes = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LogisticsNodeEntity node) {
                nodes.add(node);
            }
        }

        if (nodes.size() <= limit) {
            allowedNodeIds = null;
            return;
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        nodes.sort(Comparator.comparingDouble(node -> node.distanceToSqr(px, py, pz)));

        Set<Integer> ids = new HashSet<>(limit * 2);
        for (int i = 0; i < limit; i++) {
            ids.add(nodes.get(i).getId());
        }
        allowedNodeIds = ids;
    }

    private static String buildChannelDebugText(LogisticsNodeEntity entity) {
        StringBuilder channels = new StringBuilder("Ch: ");
        for (int i = 0; i < entity.getChannels().length; i++) {
            ChannelData channel = entity.getChannel(i);
            if (channel != null && channel.isEnabled()) {
                channels.append(i).append(' ');
            }
        }
        return channels.toString();
    }

    private static void addHighlightBox(Matrix4f matrix, VertexConsumer buffer, float r, float g, float b, float a) {
        float minX = -0.501f;
        float maxX = 0.501f;
        float minY = -0.001f;
        float maxY = 1.001f;
        float minZ = -0.501f;
        float maxZ = 0.501f;

        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
    }
}
