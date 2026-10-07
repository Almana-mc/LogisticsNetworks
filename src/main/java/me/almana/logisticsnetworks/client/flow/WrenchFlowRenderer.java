package me.almana.logisticsnetworks.client.flow;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.Shaders;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.render.LogisticsNodeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class WrenchFlowRenderer {
    // skips Sable plotgrid positions
    private static final double OFFSCREEN_RANGE_SQR = 96 * 96;
    private static final ContextKey<FlowRenderState> FLOW = new ContextKey<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "flow_lines"));
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
        if (ready(minecraft) && !minecraft.isPaused()) ANIMATION.tick(flow.speed());
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        event.getRenderState().setRenderData(FLOW, null);
        if (Shaders.renderingShadowPass() || !ready(Minecraft.getInstance())) return;
        List<FlowTopology.Node> nodes = new ArrayList<>();
        Map<UUID, FlowAnchor> anchors = new HashMap<>();
        for (var entity : event.getRenderState().entityRenderStates) {
            if (!(entity instanceof LogisticsNodeRenderState node) || node.flowChannels == 0
                    || node.flowNetworkId == null || !(node.renderVisible || node.wrenchVisible || node.highlighted)) continue;
            nodes.add(new FlowTopology.Node(node.flowNodeId, node.flowNetworkId, node.flowChannels));
            anchors.put(node.flowNodeId, node.flowAnchor);
        }
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getRenderState().cameraRenderState.pos;
        if (flow.offscreen()) recordUnrendered(event.getLevel(), partialTick, camera, nodes, anchors);
        updateTopology(nodes.stream().sorted(Comparator.comparing(FlowTopology.Node::id)).toList());
        double now = ANIMATION.distance(partialTick, flow.speed());
        List<FlowRenderState.Route> routes = new ArrayList<>();
        for (FlowTopology.Bundle route : topology) {
            FlowBundle bundle = BUNDLES.computeIfAbsent(route.key(), ignored -> new FlowBundle(now, flow.style()));
            bundle.update(route, anchors, now);
            routes.add(new FlowRenderState.Route(bundle.segments(), route.key().type(), now - bundle.startedAt()));
        }
        event.getRenderState().setRenderData(FLOW, FlowRenderState.capture(routes, camera, flow));
    }

    // ponytail: per-frame full entity scan
    private static void recordUnrendered(ClientLevel level, float partialTick, Vec3 camera,
                                         List<FlowTopology.Node> nodes, Map<UUID, FlowAnchor> anchors) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LogisticsNodeEntity node) || anchors.containsKey(node.getUUID())
                    || !node.isActive() || node.getNetworkId() == null || node.getRouteChannels() == 0) continue;
            Vec3 position = node.getPosition(partialTick);
            if (position.distanceToSqr(camera) > OFFSCREEN_RANGE_SQR) continue;
            nodes.add(new FlowTopology.Node(node.getUUID(), node.getNetworkId(), node.getRouteChannels()));
            anchors.put(node.getUUID(), new FlowAnchor(position.add(0, 0.5, 0), new Quaternionf()));
        }
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        if (Shaders.renderingShadowPass()) return;
        FlowRenderState frame = event.getLevelRenderState().getRenderData(FLOW);
        if (frame == null || frame.routes().isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),
                FlowRenderTypes.base(frame.throughBlocks()), (pose, buffer) -> FlowLines.drawBase(frame, pose, buffer));
        if (frame.pulses()) {
            event.getSubmitNodeCollector().order(1).submitCustomGeometry(event.getPoseStack(),
                    FlowRenderTypes.pulse(frame.throughBlocks()), (pose, buffer) -> FlowLines.drawPulses(frame, pose, buffer));
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

    private static void updateTopology(List<FlowTopology.Node> snapshot) {
        boolean changed = !flow.equals(appliedFlow);
        if (!changed && snapshot.equals(nodeSnapshot)) return;
        if (changed) BUNDLES.clear();
        appliedFlow = flow;
        nodeSnapshot = snapshot;
        topology = FlowTopology.build(snapshot, flow);
        var keys = topology.stream().map(FlowTopology.Bundle::key).collect(java.util.stream.Collectors.toSet());
        BUNDLES.keySet().retainAll(keys);
    }

    private static void clear() {
        ANIMATION.reset();
        BUNDLES.clear();
        nodeSnapshot = List.of();
        topology = List.of();
        appliedFlow = null;
        world = null;
    }
}
