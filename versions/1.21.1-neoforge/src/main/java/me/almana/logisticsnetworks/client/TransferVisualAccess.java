package me.almana.logisticsnetworks.client;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

final class TransferVisualAccess {

    private TransferVisualAccess() {
    }

    static boolean isWithinWrenchRenderLimit(int entityId) {
        return LogisticsNodeRenderer.isWithinWrenchRenderLimit(entityId);
    }

    static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(new Vector3f(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F), scale);
    }
}
