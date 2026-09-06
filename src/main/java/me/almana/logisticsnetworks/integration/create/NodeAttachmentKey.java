// Enable when 26.1.2 is supported.
/*
package me.almana.logisticsnetworks.integration.create;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record NodeAttachmentKey(Optional<UUID> contraptionId, BlockPos position) {
    public static NodeAttachmentKey world(BlockPos position) {
        return new NodeAttachmentKey(Optional.empty(), position.immutable());
    }

    public static NodeAttachmentKey mounted(UUID contraptionId, BlockPos localPos) {
        return new NodeAttachmentKey(Optional.of(contraptionId), localPos.immutable());
    }
}
*/
