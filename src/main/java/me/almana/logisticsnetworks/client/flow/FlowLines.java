package me.almana.logisticsnetworks.client.flow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.world.phys.Vec3;

final class FlowLines {
    private FlowLines() {
    }

    static void drawBase(FlowRenderState frame, PoseStack.Pose pose, VertexConsumer base) {
        for (FlowRenderState.Route route : frame.routes()) {
            for (FlowSegment segment : route.segments()) {
                double visible = FlowAnimation.revealed(segment.length(), segment.revealDistance(), route.travelled());
                if (visible > 1.0E-8) line(base, pose, frame.camera(), segment, 0, visible, color(route.type()),
                        frame.opacity(), frame.opacity(), frame.width());
            }
        }
    }

    static void drawPulses(FlowRenderState frame, PoseStack.Pose pose, VertexConsumer pulse) {
        for (FlowRenderState.Route route : frame.routes()) {
            for (FlowSegment segment : route.segments()) {
                double visible = FlowAnimation.revealed(segment.length(), segment.revealDistance(), route.travelled());
                pulses(pulse, pose, frame, segment, visible, route.travelled(), brighten(color(route.type())));
            }
        }
    }

    private static void pulses(VertexConsumer buffer, PoseStack.Pose pose, FlowRenderState frame, FlowSegment segment,
                               double visible, double travelled, int color) {
        double spacing = frame.spacing();
        double length = Math.min(frame.pulseLength(), spacing);
        double head = FlowAnimation.pulseHead(segment.phaseDistance(), travelled, spacing);
        for (; head < visible + length; head += spacing) {
            double start = Math.max(0, head - length);
            double end = Math.min(visible, head);
            if (end - start < 1.0E-8) continue;
            float first = (float) ((1 - (head - start) / length) * frame.opacity());
            float last = (float) ((1 - (head - end) / length) * frame.opacity());
            line(buffer, pose, frame.camera(), segment, start, end, color, first, last, frame.width() * 1.5F);
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, FlowSegment segment,
                             double start, double end, int color, float firstAlpha, float lastAlpha, float width) {
        Vec3 direction = segment.end().subtract(segment.start()).normalize();
        Vec3 origin = segment.start().subtract(camera);
        vertex(buffer, pose, origin.add(direction.scale(start)), direction, color, firstAlpha, width);
        vertex(buffer, pose, origin.add(direction.scale(end)), direction, color, lastAlpha, width);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point, Vec3 normal, int color, float alpha, float width) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(width);
    }

    private static int brighten(int color) {
        int red = (color >> 16 & 255) + (255 - (color >> 16 & 255)) / 2;
        int green = (color >> 8 & 255) + (255 - (color >> 8 & 255)) / 2;
        int blue = (color & 255) + (255 - (color & 255)) / 2;
        return red << 16 | green << 8 | blue;
    }

    private static int color(ChannelType type) {
        return switch (type) {
            case ITEM -> 0xB87D1F;
            case FLUID -> 0x1C94AC;
            case ENERGY -> 0xB43D3D;
            case CHEMICAL -> 0x2E944F;
            case SOURCE -> 0x7D49B8;
        };
    }
}
