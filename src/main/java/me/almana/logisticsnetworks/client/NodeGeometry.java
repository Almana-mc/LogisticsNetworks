package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NodeGeometry {

    private static final float FRAME = 2.0f / 16.0f;
    private static final float EPS = 0.001f;
    private static final Direction[] LOW = {Direction.WEST, Direction.DOWN, Direction.NORTH};
    private static final Direction[] HIGH = {Direction.EAST, Direction.UP, Direction.SOUTH};

    private static final Map<GeometryKey, Quad[]> QUADS = new HashMap<>();

    private NodeGeometry() {
    }

    public static void emit(Matrix4f matrix, VertexConsumer buffer, int connections, int packedLight, int color,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        GeometryKey key = new GeometryKey(connections, minX, minY, minZ, maxX, maxY, maxZ);
        for (Quad quad : QUADS.computeIfAbsent(key, NodeGeometry::create)) {
            quad.emit(matrix, buffer, packedLight, color);
        }
    }

    private static Quad[] create(GeometryKey key) {
        float[] min = {key.minX(), key.minY(), key.minZ()};
        float[] max = {key.maxX(), key.maxY(), key.maxZ()};
        List<Quad> quads = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if (!NodeConnectionMask.has(key.connections(), face)) {
                addFace(quads, key.connections(), face, min, max);
            }
        }
        return quads.toArray(Quad[]::new);
    }

    // 9-slice frame, connected borders dropped
    private static void addFace(List<Quad> quads, int mask, Direction face, float[] min, float[] max) {
        int a = face.getAxis().ordinal();
        int u = a == 0 ? 1 : 0;
        int w = a == 2 ? 1 : 2;
        float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[a] + EPS : min[a] - EPS;
        float[] us = cuts(mask, u, min[u], max[u]);
        float[] ws = cuts(mask, w, min[w], max[w]);

        boolean lone = !connected(mask, LOW[u]) && !connected(mask, HIGH[u])
                && !connected(mask, LOW[w]) && !connected(mask, HIGH[w]);
        if (lone && isUnit(min[u], max[u]) && isUnit(min[w], max[w])) {
            quads.add(quad(face, a, u, w, plane, us[0], us[3], ws[0], ws[3], 0.0f, 1.0f, 0.0f, 1.0f));
            return;
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (us[i + 1] <= us[i] || ws[j + 1] <= ws[j]) continue;
                Direction du = side(u, i);
                Direction dw = side(w, j);
                if (!drawn(mask, du, dw)) continue;
                boolean inner = du != null && dw != null && connected(mask, du) && connected(mask, dw);
                float[] uvU = uv(i, du != null && connected(mask, du) && !inner);
                float[] uvW = uv(j, dw != null && connected(mask, dw));
                quads.add(quad(face, a, u, w, plane, us[i], us[i + 1], ws[j], ws[j + 1],
                        uvU[0], uvU[1], uvW[0], uvW[1]));
            }
        }
    }

    private static float[] cuts(int mask, int axis, float min, float max) {
        float frame = Math.min(FRAME, (max - min) * 0.5f);
        float low = connected(mask, LOW[axis]) ? min : min - EPS;
        float high = connected(mask, HIGH[axis]) ? max : max + EPS;
        return new float[] {low, min + frame, max - frame, high};
    }

    private static boolean isUnit(float min, float max) {
        return Math.abs(max - min - 1.0f) < 1.0E-4f;
    }

    private static Direction side(int axis, int cell) {
        return cell == 0 ? LOW[axis] : cell == 2 ? HIGH[axis] : null;
    }

    private static boolean drawn(int mask, Direction du, Direction dw) {
        if (du == null && dw == null) return false;
        if (du == null) return !connected(mask, dw);
        if (dw == null) return !connected(mask, du);
        return !connected(mask, du) || !connected(mask, dw) || NodeConnectionMask.hasCorner(mask, du, dw);
    }

    private static float[] uv(int cell, boolean connected) {
        return switch (cell) {
            case 0 -> connected ? new float[] {2 / 16f, 4 / 16f} : new float[] {0.0f, 2 / 16f};
            case 2 -> connected ? new float[] {12 / 16f, 14 / 16f} : new float[] {14 / 16f, 1.0f};
            default -> new float[] {2 / 16f, 14 / 16f};
        };
    }

    private static boolean connected(int mask, Direction direction) {
        return NodeConnectionMask.has(mask, direction);
    }

    private static Quad quad(Direction face, int a, int u, int w, float plane, float u0, float u1, float w0,
            float w1, float tu0, float tu1, float tw0, float tw1) {
        float[][] corners = {{u0, w0, tu0, tw0}, {u0, w1, tu0, tw1}, {u1, w1, tu1, tw1}, {u1, w0, tu1, tw0}};
        boolean flip = (a == 1) != (face.getAxisDirection() == Direction.AxisDirection.POSITIVE);
        float[] xyz = new float[12];
        float[] uv = new float[8];
        for (int k = 0; k < 4; k++) {
            float[] corner = corners[flip ? 3 - k : k];
            xyz[k * 3 + a] = plane;
            xyz[k * 3 + u] = corner[0];
            xyz[k * 3 + w] = corner[1];
            uv[k * 2] = corner[2];
            uv[k * 2 + 1] = corner[3];
        }
        return new Quad(xyz, uv, face.getNormal());
    }

    private record Quad(float[] xyz, float[] uv, Vec3i normal) {
        void emit(Matrix4f matrix, VertexConsumer buffer, int packedLight, int color) {
            for (int k = 0; k < 4; k++) {
                buffer.addVertex(matrix, xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2])
                        .setColor(color)
                        .setUv(uv[k * 2], uv[k * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(normal.getX(), normal.getY(), normal.getZ());
            }
        }
    }

    private record GeometryKey(int connections, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ) {
    }
}
