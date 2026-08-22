package me.almana.logisticsnetworks.client;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

final class NodeHighlightQueue {
    private static final List<HighlightRequest> REQUESTS = new ArrayList<>();

    private NodeHighlightQueue() {
    }

    static void add(Vec3 position, Quaternionf rotation, float red, float green, float blue, float alpha,
            boolean xray) {
        REQUESTS.add(new HighlightRequest(position, new Quaternionf(rotation), red, green, blue, alpha, xray));
    }

    static List<HighlightRequest> drain(RenderLevelStageEvent.Stage stage) {
        if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES || REQUESTS.isEmpty()) {
            return List.of();
        }
        List<HighlightRequest> requests = List.copyOf(REQUESTS);
        REQUESTS.clear();
        return requests;
    }

    record HighlightRequest(Vec3 position, Quaternionf rotation, float red, float green, float blue, float alpha,
            boolean xray) {
    }
}
