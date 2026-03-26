package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.render.NodeRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class ModRenderTypes {

    public static final RenderType OVERLAY = NodeRenderTypes.overlay();
    public static final RenderType OVERLAY_XRAY = NodeRenderTypes.overlayXray();

    private ModRenderTypes() {
    }
}
