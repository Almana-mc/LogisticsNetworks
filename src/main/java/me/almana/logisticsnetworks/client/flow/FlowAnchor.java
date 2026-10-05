package me.almana.logisticsnetworks.client.flow;

import com.mojang.blaze3d.vertex.PoseStack;
import me.almana.logisticsnetworks.integration.create.NodeRenderContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record FlowAnchor(Vec3 position, Quaternionf rotation) {
    private static final double BOUNDARY_PRECISION = 1_000_000;

    static FlowAnchor fromRenderPose(PoseStack.Pose pose, Vec3 cameraPosition) {
        Vector3f center = pose.pose().transformPosition(new Vector3f(0, 0.5F, 0));
        Quaternionf rotation = new Quaternionf().setFromNormalized(pose.normal()).normalize();
        return new FlowAnchor(cameraPosition.add(center.x, center.y, center.z), rotation);
    }

    static FlowAnchor fromContext(NodeRenderContext context) {
        Vector3f up = context.rotation().transform(new Vector3f(0, 0.5F, 0));
        return new FlowAnchor(context.position().add(up.x, up.y, up.z), new Quaternionf(context.rotation()));
    }

    boolean contains(Vec3 point) {
        Vector3f local = local(point.subtract(position));
        return Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z))) < 0.52;
    }

    Vec3 boundary(Vec3 inside, Vec3 outside) {
        Vector3f start = local(inside.subtract(position));
        Vector3f delta = local(outside.subtract(inside));
        double fraction = 1;
        for (int axis = 0; axis < 3; axis++) {
            double direction = delta.get(axis);
            if (Math.abs(direction) > 1.0E-8) {
                fraction = Math.min(fraction, (Math.copySign(0.52, direction) - start.get(axis)) / direction);
            }
        }
        Vec3 boundary = inside.lerp(outside, fraction);
        if (inside.x != outside.x) return new Vec3(snap(boundary.x), inside.y, inside.z);
        if (inside.y != outside.y) return new Vec3(inside.x, snap(boundary.y), inside.z);
        return new Vec3(inside.x, inside.y, snap(boundary.z));
    }

    private static double snap(double coordinate) {
        return Math.rint(coordinate * BOUNDARY_PRECISION) / BOUNDARY_PRECISION;
    }

    private Vector3f local(Vec3 point) {
        return new Vector3f((float) point.x, (float) point.y, (float) point.z)
                .rotate(new Quaternionf(rotation).conjugate());
    }
}
