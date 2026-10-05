package me.almana.logisticsnetworks.client.flow;

import com.mojang.blaze3d.vertex.PoseStack;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.ModRenderTypes;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.create.NodeRenderContext;
import me.almana.logisticsnetworks.integration.iris.IrisCompat;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class WrenchFlowRenderer {
    // skips Sable plotgrid positions
    private static final double OFFSCREEN_RANGE_SQR = 96 * 96;
    private static final FlowFrame FRAME = new FlowFrame();
    private static final FlowAnimation ANIMATION = new FlowAnimation();
    private static final Map<FlowTopology.Key, FlowBundle> BUNDLES = new HashMap<>();
    private static List<FlowTopology.Node> nodeSnapshot = List.of();
    private static List<FlowTopology.Bundle> topology = List.of();
    private static WrenchFlow flow = WrenchFlow.DEFAULT;
    private static WrenchFlow appliedFlow;
    private static ClientLevel world;

    private WrenchFlowRenderer() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ready(minecraft) && !minecraft.isPaused()) {
            ANIMATION.tick(flow.speed());
        }
    }

    public static void queue(LogisticsNodeEntity node, PoseStack.Pose pose, Vec3 cameraPosition) {
        if (flow.enabled() && routed(node)) {
            FRAME.record(topologyNode(node), FlowAnchor.fromRenderPose(pose, cameraPosition));
        }
    }

    // ponytail: per-frame full entity scan
    private static void recordUnrendered(ClientLevel level, float partialTick, Vec3 camera) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LogisticsNodeEntity node) || FRAME.contains(node.getUUID()) || !routed(node)) continue;
            NodeRenderContext context = CreateCompat.getRenderContext(node, partialTick);
            if (context != null && context.position().distanceToSqr(camera) <= OFFSCREEN_RANGE_SQR) FRAME.record(topologyNode(node), FlowAnchor.fromContext(context));
        }
    }

    private static boolean routed(LogisticsNodeEntity node) {
        return node.isActive() && node.getNetworkId() != null && node.getRouteChannels() != 0;
    }

    private static FlowTopology.Node topologyNode(LogisticsNodeEntity node) {
        return new FlowTopology.Node(node.getUUID(), node.getNetworkId(), node.getRouteChannels());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (IrisCompat.isRenderingShadowPass()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ready(Minecraft.getInstance());
            FRAME.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(minecraft)) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (flow.offscreen()) recordUnrendered(minecraft.level, partialTick, event.getCamera().getPosition());
        updateTopology();
        double now = ANIMATION.distance(partialTick, flow.speed());
        Map<UUID, FlowAnchor> anchors = FRAME.anchors();
        var buffers = minecraft.renderBuffers().bufferSource();
        buffers.endLastBatch();
        ModRenderTypes.refreshFlowState(flow.thickness(), flow.throughBlocks());
        var poses = event.getPoseStack();
        poses.pushPose();
        poses.mulPose(event.getModelViewMatrix());
        if (IrisCompat.isShaderPackInUse()) minecraft.getMainRenderTarget().bindWrite(false);
        try {
            draw(event, now, anchors);
            buffers.endBatch(ModRenderTypes.FLOW_LINES);
            buffers.endBatch(ModRenderTypes.FLOW_PULSES);
        } finally {
            poses.popPose();
            FRAME.clear();
        }
    }

    private static void draw(RenderLevelStageEvent event, double now, Map<UUID, FlowAnchor> anchors) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var base = buffers.getBuffer(ModRenderTypes.FLOW_LINES);
        for (FlowTopology.Bundle route : topology) {
            FlowBundle bundle = BUNDLES.computeIfAbsent(route.key(), ignored -> new FlowBundle(now, flow.style()));
            bundle.update(route, anchors, now);
            FlowLines.drawBase(bundle, route.key().type(), flow, now, event.getPoseStack().last(),
                    event.getCamera().getPosition(), base);
        }
        if (!flow.pulses()) return;
        var pulse = buffers.getBuffer(ModRenderTypes.FLOW_PULSES);
        for (FlowTopology.Bundle route : topology) {
            FlowLines.drawPulses(BUNDLES.get(route.key()), route.key().type(), flow, now, event.getPoseStack().last(),
                    event.getCamera().getPosition(), pulse);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static boolean ready(Minecraft minecraft) {
        ItemStack wrench = minecraft.player == null ? ItemStack.EMPTY : heldWrench(minecraft.player);
        flow = wrench.isEmpty() ? WrenchFlow.DEFAULT : WrenchItem.getFlow(wrench);
        if (minecraft.level == null || wrench.isEmpty() || !flow.enabled()) {
            clear();
            return false;
        }
        if (world != minecraft.level) {
            clear();
            world = minecraft.level;
        }
        return true;
    }

    private static ItemStack heldWrench(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(Registration.WRENCH.get())) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(Registration.WRENCH.get()) ? off : ItemStack.EMPTY;
    }

    private static void updateTopology() {
        List<FlowTopology.Node> snapshot = FRAME.nodes();
        boolean changed = !flow.equals(appliedFlow);
        if (!changed && snapshot.equals(nodeSnapshot)) return;
        if (changed) BUNDLES.clear();
        appliedFlow = flow;
        nodeSnapshot = List.copyOf(snapshot);
        topology = FlowTopology.build(snapshot, flow);
        var keys = topology.stream().map(FlowTopology.Bundle::key).collect(java.util.stream.Collectors.toSet());
        BUNDLES.keySet().retainAll(keys);
    }

    private static void clear() {
        FRAME.clear();
        ANIMATION.reset();
        BUNDLES.clear();
        nodeSnapshot = List.of();
        topology = List.of();
        appliedFlow = null;
        world = null;
    }
}
