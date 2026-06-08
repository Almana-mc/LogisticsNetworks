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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogisticsNodeRenderer extends EntityRenderer<LogisticsNodeEntity, LogisticsNodeRenderState> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "textures/entity/node.png");
    private static final float BOUNDS_OFFSET = 0.5f;
    private static final float BLOCK_MIN = -0.5f;
    private static final float BLOCK_MAX = 0.5f;
    private static final float BLOCK_BOTTOM = 0.0f;
    private static final float BLOCK_TOP = 1.0f;
    private static final float HIGHLIGHT_EPS = 0.001f;
    private static final double SHAPE_SIDE_EPS = 1.0E-4;

    private static Set<Integer> allowedNodeIds;
    private static long lastComputeTick = Long.MIN_VALUE;
    private static final Map<BlockPos, LogisticsNodeEntity> nodesByAttachedPos = new HashMap<>();
    private static long lastLookupTick = Long.MIN_VALUE;

    public LogisticsNodeRenderer(EntityRendererProvider.Context context) {
        super(context);
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
        state.networkColor = entity.getNetworkColor();
        state.connections = ClientConfig.connectedNodeTextures ? getConnectionMask(entity) : NodeConnectionMask.NONE;
        state.debugNodeId = "";
        state.debugChannels = "";
        updateRenderBounds(entity, state);

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

        submitNodeGeometry(state, poseStack, submitNodeCollector);

        if (state.highlighted) {
            submitHighlightBox(state, poseStack, submitNodeCollector, 0.15f, 0.45f, 1.0f, 0.35f, true);
        } else if (state.wrenchVisible) {
            float wr = ((state.networkColor >> 16) & 0xFF) / 255f;
            float wg = ((state.networkColor >> 8) & 0xFF) / 255f;
            float wb = (state.networkColor & 0xFF) / 255f;
            submitHighlightBox(state, poseStack, submitNodeCollector, wr, wg, wb, 0.35f, false);
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

    private void submitNodeGeometry(LogisticsNodeRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector) {
        int color = state.renderVisible ? -1 : 0x55FFFFFF;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                NodeRenderTypes.node(TEXTURE),
                (pose, buffer) -> {
                    NodeGeometry.emit(pose.pose(), buffer, state.connections, state.lightCoords, color,
                            state.minX, state.minY, state.minZ, state.maxX, state.maxY, state.maxZ);
                    NodeGeometry.emitBridges(pose.pose(), buffer, state.bridgeConnections, state.lightCoords, color,
                            state.minX, state.minY, state.minZ, state.maxX, state.maxY, state.maxZ);
                });
    }

    private void submitHighlightBox(LogisticsNodeRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, float r, float g, float b, float a, boolean xray) {
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                xray ? NodeRenderTypes.overlayXray() : NodeRenderTypes.overlay(),
                (pose, buffer) -> addHighlightBox(pose.pose(), buffer, state, r, g, b, a));
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

    private static void updateRenderBounds(LogisticsNodeEntity entity, LogisticsNodeRenderState state) {
        state.resetBounds();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        BlockPos attachedPos = entity.getAttachedPos();
        BlockState blockState = mc.level.getBlockState(attachedPos);
        VoxelShape shape = blockState.getShape(mc.level, attachedPos, CollisionContext.empty());
        if (shape.isEmpty()) {
            return;
        }

        AABB bounds = shape.bounds();
        state.bridgeConnections = bridgeConnectionsForBounds(state.connections, bounds);
        int boundsConnections = trimConnectionsToBounds(state.connections, bounds);
        float minX = (float) bounds.minX - BOUNDS_OFFSET;
        float minY = (float) bounds.minY;
        float minZ = (float) bounds.minZ - BOUNDS_OFFSET;
        float maxX = (float) bounds.maxX - BOUNDS_OFFSET;
        float maxY = (float) bounds.maxY;
        float maxZ = (float) bounds.maxZ - BOUNDS_OFFSET;

        if (NodeConnectionMask.has(boundsConnections, Direction.WEST)) minX = BLOCK_MIN;
        if (NodeConnectionMask.has(boundsConnections, Direction.EAST)) maxX = BLOCK_MAX;
        if (NodeConnectionMask.has(boundsConnections, Direction.DOWN)) minY = BLOCK_BOTTOM;
        if (NodeConnectionMask.has(boundsConnections, Direction.UP)) maxY = BLOCK_TOP;
        if (NodeConnectionMask.has(boundsConnections, Direction.NORTH)) minZ = BLOCK_MIN;
        if (NodeConnectionMask.has(boundsConnections, Direction.SOUTH)) maxZ = BLOCK_MAX;

        if (maxX > minX && maxY > minY && maxZ > minZ) {
            state.setBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static int bridgeConnectionsForBounds(int connections, AABB bounds) {
        int bridges = NodeConnectionMask.NONE;
        for (Direction direction : Direction.values()) {
            if (NodeConnectionMask.has(connections, direction) && !touchesSide(bounds, direction)) {
                bridges = NodeConnectionMask.add(bridges, direction);
            }
        }
        return bridges;
    }

    private static int trimConnectionsToBounds(int connections, AABB bounds) {
        int trimmed = connections;
        for (Direction direction : Direction.values()) {
            if (NodeConnectionMask.has(trimmed, direction) && !touchesSide(bounds, direction)) {
                trimmed = NodeConnectionMask.remove(trimmed, direction);
            }
        }
        return trimmed;
    }

    private static boolean touchesSide(AABB bounds, Direction direction) {
        return switch (direction) {
            case WEST -> bounds.minX <= SHAPE_SIDE_EPS;
            case EAST -> bounds.maxX >= 1.0 - SHAPE_SIDE_EPS;
            case DOWN -> bounds.minY <= SHAPE_SIDE_EPS;
            case UP -> bounds.maxY >= 1.0 - SHAPE_SIDE_EPS;
            case NORTH -> bounds.minZ <= SHAPE_SIDE_EPS;
            case SOUTH -> bounds.maxZ >= 1.0 - SHAPE_SIDE_EPS;
        };
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

    private static int getConnectionMask(LogisticsNodeEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return NodeConnectionMask.NONE;
        }

        updateNodeLookup(mc);

        int mask = NodeConnectionMask.NONE;
        BlockPos attachedPos = entity.getAttachedPos();
        AABB bounds = shapeBounds(mc, attachedPos);
        if (bounds == null) {
            return NodeConnectionMask.NONE;
        }

        for (Direction direction : Direction.values()) {
            LogisticsNodeEntity neighbor = nodesByAttachedPos.get(attachedPos.relative(direction));
            if (neighbor != null && neighbor != entity && hasMatchingBounds(mc, neighbor, bounds)) {
                mask = NodeConnectionMask.add(mask, direction);
            }
        }
        for (Direction first : Direction.values()) {
            for (Direction second : Direction.values()) {
                if (first.ordinal() >= second.ordinal() || !NodeConnectionMask.isCornerPair(first, second)) {
                    continue;
                }
                if (!NodeConnectionMask.has(mask, first) || !NodeConnectionMask.has(mask, second)) {
                    continue;
                }
                LogisticsNodeEntity corner = nodesByAttachedPos.get(attachedPos.relative(first).relative(second));
                if (corner == null || !hasMatchingBounds(mc, corner, bounds)) {
                    mask = NodeConnectionMask.addCorner(mask, first, second);
                }
            }
        }
        return mask;
    }

    private static AABB shapeBounds(Minecraft mc, BlockPos pos) {
        BlockState blockState = mc.level.getBlockState(pos);
        VoxelShape shape = blockState.getShape(mc.level, pos, CollisionContext.empty());
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static boolean hasMatchingBounds(Minecraft mc, LogisticsNodeEntity node, AABB bounds) {
        if (!isRenderableNeighbor(node)) {
            return false;
        }
        AABB otherBounds = shapeBounds(mc, node.getAttachedPos());
        return otherBounds != null && sameBounds(bounds, otherBounds);
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return close(first.minX, second.minX)
                && close(first.minY, second.minY)
                && close(first.minZ, second.minZ)
                && close(first.maxX, second.maxX)
                && close(first.maxY, second.maxY)
                && close(first.maxZ, second.maxZ);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= SHAPE_SIDE_EPS;
    }

    private static boolean isRenderableNeighbor(LogisticsNodeEntity node) {
        return node.isAlive() && (node.isRenderVisible() || node.isHighlighted() || isWrenchVisible(node));
    }

    private static void updateNodeLookup(Minecraft mc) {
        long tick = mc.level.getGameTime();
        if (tick == lastLookupTick) {
            return;
        }
        lastLookupTick = tick;

        nodesByAttachedPos.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LogisticsNodeEntity node && node.isActive()) {
                nodesByAttachedPos.put(node.getAttachedPos(), node);
            }
        }
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

    private static void addHighlightBox(Matrix4f matrix, VertexConsumer buffer, LogisticsNodeRenderState state, float r,
            float g, float b, float a) {
        float minX = state.minX - HIGHLIGHT_EPS;
        float maxX = state.maxX + HIGHLIGHT_EPS;
        float minY = state.minY - HIGHLIGHT_EPS;
        float maxY = state.maxY + HIGHLIGHT_EPS;
        float minZ = state.minZ - HIGHLIGHT_EPS;
        float maxZ = state.maxZ + HIGHLIGHT_EPS;

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
