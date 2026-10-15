package me.almana.logisticsnetworks.integration.create;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public final class CreateCompat {

    private CreateCompat() {
    }

    // Stub: world nodes only
    @Nullable
    public static NodeRenderContext getRenderContext(LogisticsNodeEntity node, float partialTick) {
        return new NodeRenderContext(node.getPosition(partialTick), new Quaternionf(), node.level(),
                node.getAttachedPos(), NodeAttachmentKey.world(node.getAttachedPos()));
    }
}
