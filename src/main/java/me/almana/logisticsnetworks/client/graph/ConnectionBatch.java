package me.almana.logisticsnetworks.client.graph;

import me.almana.logisticsnetworks.data.graph.GraphPosition;

final class ConnectionBatch {
    private static final float CABLE_HALF_WIDTH = 2.25f;
    private static final float CONDUCTOR_HALF_WIDTH = 0.75f;
    private static final float PULSE_LENGTH = 14.0f;
    private static final float PULSE_SPEED = 48.0f;
    private static final float PULSE_SPACING = 80.0f;
    private static final int MAX_PULSES = 5;
    private static final int EDGE_FLOATS = 7;
    private static final int BOUNDS_FLOATS = 4;
    private final float[] geometry;
    private final float[] bounds;
    private final boolean[] active;
    private int size;

    ConnectionBatch(int capacity) {
        geometry = new float[capacity * EDGE_FLOATS];
        bounds = new float[capacity * BOUNDS_FLOATS];
        active = new boolean[capacity];
    }

    int add(GraphPosition source, GraphPosition target, float offset, float nodeRadius) {
        int index = size++;
        update(index, source, target, offset, nodeRadius);
        return index;
    }

    void update(int index, GraphPosition source, GraphPosition target, float offset, float nodeRadius) {
        float dx = target.x() - source.x();
        float dy = target.y() - source.y();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= nodeRadius * 2.0f + 2.0f) {
            active[index] = false;
            return;
        }
        active[index] = true;

        float directionX = dx / length;
        float directionY = dy / length;
        float normalX = -directionY;
        float normalY = directionX;
        float startX = source.x() + directionX * nodeRadius + normalX * offset;
        float startY = source.y() + directionY * nodeRadius + normalY * offset;
        float endX = target.x() - directionX * nodeRadius + normalX * offset;
        float endY = target.y() - directionY * nodeRadius + normalY * offset;
        int edge = index * EDGE_FLOATS;
        put(edge, startX, startY);
        put(edge + 2, endX, endY);
        geometry[edge + 4] = directionX;
        geometry[edge + 5] = directionY;
        geometry[edge + 6] = length - nodeRadius * 2.0f;

        int bound = index * BOUNDS_FLOATS;
        bounds[bound] = Math.min(startX, endX) - CABLE_HALF_WIDTH;
        bounds[bound + 1] = Math.min(startY, endY) - CABLE_HALF_WIDTH;
        bounds[bound + 2] = Math.max(startX, endX) + CABLE_HALF_WIDTH;
        bounds[bound + 3] = Math.max(startY, endY) + CABLE_HALF_WIDTH;
    }

    void drawCasing(GraphRenderState.Builder vertices, GraphCamera camera, int color) {
        for (int index = 0; index < size; index++) {
            if (!active[index] || !visible(camera, index)) {
                continue;
            }
            int edge = index * EDGE_FLOATS;
            addLine(vertices, geometry[edge], geometry[edge + 1], geometry[edge + 2],
                    geometry[edge + 3], -geometry[edge + 5], geometry[edge + 4], CABLE_HALF_WIDTH, color);
        }
    }

    void drawSignal(GraphRenderState.Builder vertices, GraphCamera camera, int color) {
        for (int index = 0; index < size; index++) {
            if (!active[index] || !visible(camera, index)) {
                continue;
            }
            int edge = index * EDGE_FLOATS;
            float normalX = -geometry[edge + 5];
            float normalY = geometry[edge + 4];
            addLine(vertices, geometry[edge], geometry[edge + 1], geometry[edge + 2],
                    geometry[edge + 3], normalX, normalY, CONDUCTOR_HALF_WIDTH, withAlpha(color, 0x70));
        }
    }

    void drawPulses(GraphRenderState.Builder vertices, GraphCamera camera, int color, boolean glow,
                    long elapsedMillis, int phaseSeed) {
        for (int index = 0; index < size; index++) {
            if (!active[index] || !visible(camera, index)) {
                continue;
            }
            int edge = index * EDGE_FLOATS;
            int count = pulseCount(geometry[edge + 6]);
            int edgeSeed = phaseSeed + index * 17;
            for (int pulse = 0; pulse < count; pulse++) {
                float distance = pulseDistance(geometry[edge + 6], elapsedMillis, pulse, count, edgeSeed);
                drawPulse(vertices, edge, distance, color, glow);
            }
        }
    }

    private void drawPulse(GraphRenderState.Builder vertices, int edge, float distance, int color,
                           boolean glow) {
        float length = geometry[edge + 6];
        float fade = Math.min(1.0f, Math.min(distance, length - distance) / 8.0f);
        if (fade <= 0.0f) {
            return;
        }
        float directionX = geometry[edge + 4];
        float directionY = geometry[edge + 5];
        float normalX = -directionY;
        float normalY = directionX;
        float tailDistance = Math.max(0.0f, distance - PULSE_LENGTH);
        float tailX = geometry[edge] + directionX * tailDistance;
        float tailY = geometry[edge + 1] + directionY * tailDistance;
        float headX = geometry[edge] + directionX * distance;
        float headY = geometry[edge + 1] + directionY * distance;
        if (glow) {
            addTaperedLine(vertices, tailX, tailY, headX, headY, normalX, normalY, 1.0f, 3.2f,
                    withAlpha(color, 0), withAlpha(color, Math.round(0x48 * fade)));
        }
        addTaperedLine(vertices, tailX, tailY, headX, headY, normalX, normalY, 0.35f, 1.4f,
                withAlpha(color, 0), withAlpha(color, Math.round(0xF0 * fade)));
        addDiamond(vertices, headX, headY, directionX, directionY, normalX, normalY,
                withAlpha(color, Math.round(0xFF * fade)));
    }

    private void addLine(GraphRenderState.Builder vertices, float startX, float startY, float endX,
                         float endY, float normalX, float normalY, float halfWidth, int color) {
        addTaperedLine(vertices, startX, startY, endX, endY, normalX, normalY, halfWidth, halfWidth,
                color, color);
    }

    private void addTaperedLine(GraphRenderState.Builder vertices, float startX, float startY, float endX,
                                float endY, float normalX, float normalY, float startWidth, float endWidth,
                                int startColor, int endColor) {
        vertices.vertex(startX + normalX * startWidth, startY + normalY * startWidth, startColor);
        vertices.vertex(endX + normalX * endWidth, endY + normalY * endWidth, endColor);
        vertices.vertex(endX - normalX * endWidth, endY - normalY * endWidth, endColor);
        vertices.vertex(startX - normalX * startWidth, startY - normalY * startWidth, startColor);
    }

    private void addDiamond(GraphRenderState.Builder vertices, float centerX, float centerY,
                            float directionX, float directionY, float normalX, float normalY, int color) {
        vertices.vertex(centerX + directionX * 2.4f, centerY + directionY * 2.4f, color);
        vertices.vertex(centerX + normalX * 1.8f, centerY + normalY * 1.8f, color);
        vertices.vertex(centerX - directionX * 2.4f, centerY - directionY * 2.4f, color);
        vertices.vertex(centerX - normalX * 1.8f, centerY - normalY * 1.8f, color);
    }

    private boolean visible(GraphCamera camera, int index) {
        int bound = index * BOUNDS_FLOATS;
        float screenMinX = camera.screenX(bounds[bound]);
        float screenMinY = camera.screenY(bounds[bound + 1]);
        float screenMaxX = camera.screenX(bounds[bound + 2]);
        float screenMaxY = camera.screenY(bounds[bound + 3]);
        return screenMaxX >= camera.x() && screenMinX < camera.x() + camera.width()
                && screenMaxY >= camera.y() && screenMinY < camera.y() + camera.height();
    }

    private void put(int offset, float x, float y) {
        geometry[offset] = x;
        geometry[offset + 1] = y;
    }

    static int pulseCount(float length) {
        return Math.max(1, Math.min(MAX_PULSES, Math.round(length / PULSE_SPACING)));
    }

    static float pulseDistance(float length, long elapsedMillis, int pulseIndex, int pulseCount, int phaseSeed) {
        double spacing = length / pulseCount;
        double traveled = elapsedMillis * PULSE_SPEED / 1_000.0;
        return (float) ((traveled + phaseSeed + pulseIndex * spacing) % length);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
