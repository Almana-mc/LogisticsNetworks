package me.almana.logisticsnetworks.client.flow;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

record FlowRenderState(List<Route> routes, Vec3 camera, float width, float opacity, boolean pulses,
                       double spacing, double pulseLength, boolean throughBlocks) {
    FlowRenderState {
        routes = List.copyOf(routes);
    }

    static FlowRenderState capture(List<Route> routes, Vec3 camera) {
        return new FlowRenderState(routes, camera, (float) ClientConfig.flowLineThickness,
                (float) ClientConfig.flowLineOpacity, ClientConfig.flowLinePulses,
                ClientConfig.flowLinePulseSpacing, ClientConfig.flowLinePulseLength,
                ClientConfig.flowLinesThroughBlocks);
    }

    record Route(List<FlowSegment> segments, ChannelType type, double travelled) {
        Route {
            segments = List.copyOf(segments);
        }
    }
}
