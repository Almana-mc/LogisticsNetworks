package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.LogisticsNetworks;
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
import net.minecraft.core.Direction;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
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

public class LogisticsNodeRenderer extends EntityRenderer<LogisticsNodeEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/entity/node.png");
    private static final float BOUNDS_OFFSET = 0.5f;
    private static final float BLOCK_MIN = -0.5f;
    private static final float BLOCK_MAX = 0.5f;
    private static final float BLOCK_BOTTOM = 0.0f;
    private static final float BLOCK_TOP = 1.0f;
    private static final double SHAPE_SIDE_EPS = 1.0E-4;
    private final NodeModel<LogisticsNodeEntity> model;

    private static final List<LogisticsNodeEntity> cachedNodeList = new ArrayList<>();
    private static Set<Integer> allowedNodeIds;
    private static Set<Integer> visibleNodeIds;
    private static long lastComputeTick = -1;
    private static final Map<BlockPos, LogisticsNodeEntity> nodesByAttachedPos = new HashMap<>();
    private static long lastLookupTick = -1;

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

    public static boolean isWithinWrenchRenderLimit(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId < 0 || minecraft.level == null
                || !(minecraft.level.getEntity(entityId) instanceof LogisticsNodeEntity))
            return false;
        updateAllowedNodes(minecraft);
        return allowedNodeIds == null || allowedNodeIds.contains(entityId);
    }

    @Override
    protected void renderNameTag(LogisticsNodeEntity entity, Component displayName, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        String networkName = entity.getNetworkName();
        String label = (networkName == null || networkName.isBlank()) ? "No Network" : networkName;
        super.renderNameTag(entity, Component.literal(label), poseStack, buffer, packedLight);
    }

    private void renderModel(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer, int light,
            boolean isVisible) {
        int color = isVisible ? -1 : 0x55FFFFFF;
        VertexConsumer consumer = buffer
                .getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        if (ClientConfig.connectedNodeTextures && renderConnectedModel(entity, poseStack, consumer, light, color)) {
            return;
        }

        poseStack.pushPose();

        float scaleXZ = 17.0f / 16.0f;
        float scaleY = 18.0f / 16.0f;
        poseStack.scale(-scaleXZ, -scaleY, scaleXZ);
        poseStack.translate(0.0, -17.0f / 16.0f - (8.0f / 18.0f), 0.0);

        float alpha = isVisible ? 1.0F : 0.33333334F;
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);

        poseStack.popPose();
    }

    private boolean renderConnectedModel(LogisticsNodeEntity entity, PoseStack poseStack, VertexConsumer consumer,
            int light, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        AABB shapeBounds = shapeBounds(mc, entity.getAttachedPos());
        if (shapeBounds == null) {
            return false;
        }

        int connections = getConnectionMask(entity);
        RenderBounds bounds = renderBounds(connections, shapeBounds);

        Matrix4f matrix = poseStack.last().pose();
        NodeGeometry.emit(matrix, consumer, bounds.connections(), light, color,
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        return true;
    }

    private void renderWrenchOverlay(LogisticsNodeEntity entity, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        int networkColor = entity.getNetworkColor();
        float wr = ((networkColor >> 16) & 0xFF) / 255f;
        float wg = ((networkColor >> 8) & 0xFF) / 255f;
        float wb = (networkColor & 0xFF) / 255f;
        renderHighlightBox(poseStack, buffer, wr, wg, wb, 0.35f, false);

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
        VertexConsumer builder = buffer.getBuffer(xray && !Shaders.shadersActive()
                ? ModRenderTypes.OVERLAY_XRAY : ModRenderTypes.OVERLAY);
        Matrix4f matrix = poseStack.last().pose();

        float minX = -0.501f, maxX = 0.501f;
        float minY = -0.001f, maxY = 1.001f;
        float minZ = -0.501f, maxZ = 0.501f;

        addVertex(builder, matrix, minX, maxY, minZ, r, g, b, a);
        addVertex(builder, matrix, minX, maxY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, maxY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, maxY, minZ, r, g, b, a);

        addVertex(builder, matrix, maxX, minY, minZ, r, g, b, a);
        addVertex(builder, matrix, maxX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, minZ, r, g, b, a);

        addVertex(builder, matrix, minX, maxY, minZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, minZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, minX, maxY, maxZ, r, g, b, a);

        addVertex(builder, matrix, maxX, maxY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, minY, minZ, r, g, b, a);
        addVertex(builder, matrix, maxX, maxY, minZ, r, g, b, a);

        addVertex(builder, matrix, maxX, maxY, minZ, r, g, b, a);
        addVertex(builder, matrix, maxX, minY, minZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, minZ, r, g, b, a);
        addVertex(builder, matrix, minX, maxY, minZ, r, g, b, a);

        addVertex(builder, matrix, minX, maxY, maxZ, r, g, b, a);
        addVertex(builder, matrix, minX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, minY, maxZ, r, g, b, a);
        addVertex(builder, matrix, maxX, maxY, maxZ, r, g, b, a);
    }

    private static void addVertex(VertexConsumer builder, Matrix4f matrix, float x, float y, float z,
            float red, float green, float blue, float alpha) {
        builder.vertex(matrix, x, y, z)
                .color((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F))
                .endVertex();
    }

    private void renderLabel(LogisticsNodeEntity entity, String text, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
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
            if (neighbor != null && neighbor != entity && hasMatchingBounds(mc, neighbor, bounds, direction)) {
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
                if (corner == null || !hasCornerBounds(mc, corner, bounds, first, second)) {
                    mask = NodeConnectionMask.addCorner(mask, first, second);
                }
            }
        }
        return mask;
    }

    private static RenderBounds renderBounds(int connections, AABB bounds) {
        int boundsConnections = trimConnectionsToBounds(connections, bounds);
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

        return new RenderBounds(minX, minY, minZ, maxX, maxY, maxZ, boundsConnections);
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

    private static AABB shapeBounds(Minecraft mc, BlockPos pos) {
        BlockState blockState = mc.level.getBlockState(pos);
        VoxelShape shape = blockState.getShape(mc.level, pos, CollisionContext.empty());
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static boolean hasMatchingBounds(Minecraft mc, LogisticsNodeEntity node, AABB bounds,
            Direction direction) {
        if (!isRenderableNeighbor(node)) {
            return false;
        }
        AABB otherBounds = shapeBounds(mc, node.getAttachedPos());
        return otherBounds != null && sameCrossSection(bounds, otherBounds, direction.getAxis());
    }

    private static boolean hasCornerBounds(Minecraft mc, LogisticsNodeEntity node, AABB bounds, Direction first,
            Direction second) {
        if (!isRenderableNeighbor(node)) {
            return false;
        }
        AABB otherBounds = shapeBounds(mc, node.getAttachedPos());
        return otherBounds != null && sameRemainingSection(bounds, otherBounds, first.getAxis(), second.getAxis());
    }

    private static boolean sameCrossSection(AABB first, AABB second, Direction.Axis axis) {
        return switch (axis) {
            case X -> sameY(first, second) && sameZ(first, second);
            case Y -> sameX(first, second) && sameZ(first, second);
            case Z -> sameX(first, second) && sameY(first, second);
        };
    }

    private static boolean sameRemainingSection(AABB first, AABB second, Direction.Axis firstAxis,
            Direction.Axis secondAxis) {
        if (firstAxis != Direction.Axis.X && secondAxis != Direction.Axis.X) {
            return sameX(first, second);
        }
        if (firstAxis != Direction.Axis.Y && secondAxis != Direction.Axis.Y) {
            return sameY(first, second);
        }
        return sameZ(first, second);
    }

    private static boolean sameX(AABB first, AABB second) {
        return close(first.minX, second.minX) && close(first.maxX, second.maxX);
    }

    private static boolean sameY(AABB first, AABB second) {
        return close(first.minY, second.minY) && close(first.maxY, second.maxY);
    }

    private static boolean sameZ(AABB first, AABB second) {
        return close(first.minZ, second.minZ) && close(first.maxZ, second.maxZ);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= SHAPE_SIDE_EPS;
    }

    private static boolean isRenderableNeighbor(LogisticsNodeEntity node) {
        return node.isAlive() && (node.isRenderVisible() || node.isHighlighted() || isWrenchVisible(node));
    }

    private static boolean isWrenchVisible(LogisticsNodeEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isHolding(Registration.WRENCH.get())) {
            return false;
        }
        updateAllowedNodes(mc);
        return allowedNodeIds == null || allowedNodeIds.contains(entity.getId());
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

    private record RenderBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
            int connections) {
    }

    @Override
    protected int getBlockLightLevel(LogisticsNodeEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(LogisticsNodeEntity entity) {
        return TEXTURE;
    }
}
