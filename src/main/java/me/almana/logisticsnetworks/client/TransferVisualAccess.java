package me.almana.logisticsnetworks.client;

//? if <26 {
/*import org.joml.Vector3f;
*///?} else {
import me.almana.logisticsnetworks.render.LogisticsNodeRenderer;
//?}
import net.minecraft.core.particles.DustParticleOptions;

final class TransferVisualAccess {

    private TransferVisualAccess() {
    }

    static boolean isWithinWrenchRenderLimit(int entityId) {
        return LogisticsNodeRenderer.isWithinWrenchRenderLimit(entityId);
    }

    static DustParticleOptions dust(int color, float scale) {
        //? if <26 {
        /*return new DustParticleOptions(new Vector3f(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F), scale);
        *///?} else {
        return new DustParticleOptions(color, scale);
        //?}
    }
}
