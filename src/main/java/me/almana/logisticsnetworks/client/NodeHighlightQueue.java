package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.integration.iris.IrisCompat;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            clear();
        }
        var renderStage = IrisCompat.isLoaded() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (stage != renderStage || REQUESTS.isEmpty()) {
            return List.of();
        }
        List<HighlightRequest> requests = List.copyOf(REQUESTS);
        REQUESTS.clear();
        return requests;
    }

    static void clear() {
        REQUESTS.clear();
    }

    record HighlightRequest(Vec3 position, Quaternionf rotation, float red, float green, float blue, float alpha,
            boolean xray) {
    }
}
