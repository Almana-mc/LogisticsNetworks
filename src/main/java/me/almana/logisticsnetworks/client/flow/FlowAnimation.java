package me.almana.logisticsnetworks.client.flow;

final class FlowAnimation {
    private double travelled;

    void tick(double speed) {
        travelled += speed / 20;
    }

    double distance(float partialTick, double speed) {
        return travelled + partialTick * speed / 20;
    }

    void reset() {
        travelled = 0;
    }

    static double revealed(double length, double distance, double travelled) {
        return Math.clamp(travelled - distance, 0, length);
    }

    static double pulseHead(double phase, double travelled, double spacing) {
        double head = travelled - phase;
        return head - Math.floor(head / spacing) * spacing;
    }
}
