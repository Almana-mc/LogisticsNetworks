package me.almana.logisticsnetworks.client.flow;

import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

record FlowRenderState(List<Route> routes, Vec3 camera, List<Integer> colors, float width, float opacity,
                       boolean pulses, double spacing, double pulseLength, boolean throughBlocks) {
    FlowRenderState {
        routes = List.copyOf(routes);
    }

    static FlowRenderState capture(List<Route> routes, Vec3 camera, WrenchFlow flow) {
        return new FlowRenderState(routes, camera, flow.colors(), (float) flow.thickness(), (float) flow.opacity(),
                flow.pulses(), flow.pulseSpacing(), flow.pulseLength(), flow.throughBlocks());
    }

    int color(ChannelType type) {
        return colors.get(type.ordinal());
    }

    record Route(List<FlowSegment> segments, ChannelType type, double travelled) {
        Route {
            segments = List.copyOf(segments);
        }
    }
}
