package me.almana.logisticsnetworks.client.graph;

import me.almana.logisticsnetworks.data.graph.GraphPosition;

import java.util.Collection;

final class GraphCamera {
    private static final float MIN_ZOOM = 0.01f;
    private static final float MAX_ZOOM = 2.5f;
    private static final float FIT_PADDING = 56.0f;

    private int x;
    private int y;
    private int width;
    private int height;
    private float centerX;
    private float centerY;
    private float zoom = 1.0f;

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    boolean contains(double screenX, double screenY) {
        return screenX >= x && screenX < x + width && screenY >= y && screenY < y + height;
    }

    float screenX(float worldX) {
        return x + width / 2.0f + (worldX - centerX) * zoom;
    }

    float screenY(float worldY) {
        return y + height / 2.0f + (worldY - centerY) * zoom;
    }

    float worldX(double screenX) {
        return centerX + ((float) screenX - x - width / 2.0f) / zoom;
    }

    float worldY(double screenY) {
        return centerY + ((float) screenY - y - height / 2.0f) / zoom;
    }

    void pan(double deltaX, double deltaY) {
        centerX -= (float) deltaX / zoom;
        centerY -= (float) deltaY / zoom;
    }

    void zoomAt(double screenX, double screenY, double amount) {
        float worldX = worldX(screenX);
        float worldY = worldY(screenY);
        zoom = clamp((float) (zoom * Math.pow(1.15, amount)), MIN_ZOOM, MAX_ZOOM);
        centerX = worldX - ((float) screenX - x - width / 2.0f) / zoom;
        centerY = worldY - ((float) screenY - y - height / 2.0f) / zoom;
    }

    void fit(Collection<GraphPosition> positions) {
        if (positions.isEmpty() || width <= 0 || height <= 0) {
            reset();
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (GraphPosition position : positions) {
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
        }

        centerX = (minX + maxX) / 2.0f;
        centerY = (minY + maxY) / 2.0f;
        float graphWidth = Math.max(1.0f, maxX - minX + FIT_PADDING * 2.0f);
        float graphHeight = Math.max(1.0f, maxY - minY + FIT_PADDING * 2.0f);
        zoom = clamp(Math.min(width / graphWidth, height / graphHeight), MIN_ZOOM, MAX_ZOOM);
    }

    void reset() {
        centerX = 0.0f;
        centerY = 0.0f;
        zoom = 1.0f;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    float centerX() {
        return centerX;
    }

    float centerY() {
        return centerY;
    }

    float zoom() {
        return zoom;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
