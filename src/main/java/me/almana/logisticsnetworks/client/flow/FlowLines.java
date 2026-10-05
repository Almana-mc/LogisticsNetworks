package me.almana.logisticsnetworks.client.flow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.world.phys.Vec3;

final class FlowLines {
    private FlowLines() {
    }

    static void drawBase(FlowBundle bundle, ChannelType type, WrenchFlow flow, double now, PoseStack.Pose pose,
                         Vec3 camera, VertexConsumer base) {
        int color = flow.color(type);
        float opacity = (float) flow.opacity();
        for (FlowSegment segment : bundle.segments()) {
            double visible = FlowAnimation.revealed(segment.length(), segment.revealDistance(), now - bundle.startedAt());
            if (visible > 1.0E-8) line(base, pose, camera, segment, 0, visible, color, opacity, opacity);
        }
    }

    static void drawPulses(FlowBundle bundle, ChannelType type, WrenchFlow flow, double now, PoseStack.Pose pose,
                           Vec3 camera, VertexConsumer pulse) {
        for (FlowSegment segment : bundle.segments()) {
            double visible = FlowAnimation.revealed(segment.length(), segment.revealDistance(), now - bundle.startedAt());
            pulses(pulse, pose, camera, segment, visible, now - bundle.startedAt(), brighten(flow.color(type)), flow);
        }
    }

    private static void pulses(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, FlowSegment segment,
                               double visible, double travelled, int color, WrenchFlow flow) {
        double spacing = flow.pulseSpacing();
        double length = Math.min(flow.pulseLength(), spacing);
        double head = FlowAnimation.pulseHead(segment.phaseDistance(), travelled, spacing);
        for (; head < visible + length; head += spacing) {
            double start = Math.max(0, head - length);
            double end = Math.min(visible, head);
            if (end - start < 1.0E-8) continue;
            float first = (float) ((1 - (head - start) / length) * flow.opacity());
            float last = (float) ((1 - (head - end) / length) * flow.opacity());
            line(buffer, pose, camera, segment, start, end, color, first, last);
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, FlowSegment segment,
                             double start, double end, int color, float firstAlpha, float lastAlpha) {
        Vec3 direction = segment.end().subtract(segment.start()).normalize();
        Vec3 origin = segment.start().subtract(camera);
        vertex(buffer, pose, origin.add(direction.scale(start)), direction, color, firstAlpha);
        vertex(buffer, pose, origin.add(direction.scale(end)), direction, color, lastAlpha);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point, Vec3 normal, int color, float alpha) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int brighten(int color) {
        int red = (color >> 16 & 255) + (255 - (color >> 16 & 255)) / 2;
        int green = (color >> 8 & 255) + (255 - (color >> 8 & 255)) / 2;
        int blue = (color & 255) + (255 - (color & 255)) / 2;
        return red << 16 | green << 8 | blue;
    }
}
