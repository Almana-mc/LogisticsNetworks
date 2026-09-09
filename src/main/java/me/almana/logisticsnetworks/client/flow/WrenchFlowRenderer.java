package me.almana.logisticsnetworks.client.flow;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.Shaders;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.render.LogisticsNodeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class WrenchFlowRenderer {
    private static final ContextKey<FlowRenderState> FLOW = new ContextKey<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "flow_lines"));
    private static final FlowAnimation ANIMATION = new FlowAnimation();
    private static final Map<FlowTopology.Key, FlowBundle> BUNDLES = new HashMap<>();
    private static List<FlowTopology.Node> nodeSnapshot = List.of();
    private static List<FlowTopology.Bundle> topology = List.of();
    private static ClientLevel world;

    private WrenchFlowRenderer() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ready(minecraft) && !minecraft.isPaused()) ANIMATION.tick(ClientConfig.flowLineSpeed);
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
        updateTopology(nodes.stream().sorted(Comparator.comparing(FlowTopology.Node::id)).toList());
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = ANIMATION.distance(partialTick, ClientConfig.flowLineSpeed);
        List<FlowRenderState.Route> routes = new ArrayList<>();
        for (FlowTopology.Bundle route : topology) {
            FlowBundle bundle = BUNDLES.computeIfAbsent(route.key(), ignored -> new FlowBundle(now));
            bundle.update(route, anchors, now);
            routes.add(new FlowRenderState.Route(bundle.segments(), route.key().type(), now - bundle.startedAt()));
        }
        event.getRenderState().setRenderData(FLOW, FlowRenderState.capture(routes,
                event.getRenderState().cameraRenderState.pos));
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
        if (!ClientConfig.flowLinesEnabled || minecraft.level == null || minecraft.player == null
                || !minecraft.player.isHolding(Registration.WRENCH.get())) {
            clear();
            return false;
        }
        if (world != minecraft.level) {
            clear();
            world = minecraft.level;
        }
        return true;
    }

    private static void updateTopology(List<FlowTopology.Node> snapshot) {
        if (snapshot.equals(nodeSnapshot)) return;
        nodeSnapshot = snapshot;
        topology = FlowTopology.build(snapshot);
        var keys = topology.stream().map(FlowTopology.Bundle::key).collect(java.util.stream.Collectors.toSet());
        BUNDLES.keySet().retainAll(keys);
    }

    private static void clear() {
        ANIMATION.reset();
        BUNDLES.clear();
        nodeSnapshot = List.of();
        topology = List.of();
        world = null;
    }
}
