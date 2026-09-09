package me.almana.logisticsnetworks.client.flow;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record FlowAnchor(Vec3 position, float rotationX, float rotationY, float rotationZ, float rotationW) {
    private static final double BOUNDARY_PRECISION = 1_000_000;

    public FlowAnchor(Vec3 position, Quaternionf rotation) {
        this(position, rotation.x, rotation.y, rotation.z, rotation.w);
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
                .rotate(new Quaternionf(rotationX, rotationY, rotationZ, rotationW).conjugate());
    }
}
