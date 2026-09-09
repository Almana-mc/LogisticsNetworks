package me.almana.logisticsnetworks.client.flow;

import net.minecraft.world.phys.Vec3;

record FlowSegment(Vec3 start, Vec3 end, double phaseDistance, double revealDistance) {
    double length() {
        return start.distanceTo(end);
    }
}
