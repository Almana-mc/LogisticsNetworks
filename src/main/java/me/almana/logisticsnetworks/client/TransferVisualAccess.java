package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.render.LogisticsNodeRenderer;
import net.minecraft.core.particles.DustParticleOptions;

final class TransferVisualAccess {

    private TransferVisualAccess() {
    }

    static boolean isWithinWrenchRenderLimit(int entityId) {
        return LogisticsNodeRenderer.isWithinWrenchRenderLimit(entityId);
    }

    static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(color, scale);
    }
}
