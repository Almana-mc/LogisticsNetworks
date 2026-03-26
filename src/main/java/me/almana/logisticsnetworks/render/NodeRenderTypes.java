package me.almana.logisticsnetworks.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class NodeRenderTypes {

    private static final RenderPipeline OVERLAY_XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "pipeline/node_overlay_xray"))
            .withCull(false)
            .withDepthStencilState(Optional.empty())
            .build();

    private static final RenderType OVERLAY_XRAY = RenderType.create(
            "logisticsnetworks_node_overlay_xray",
            RenderSetup.builder(OVERLAY_XRAY_PIPELINE).sortOnUpload().createRenderSetup());

    private NodeRenderTypes() {
    }

    public static RenderType overlay() {
        return RenderTypes.debugFilledBox();
    }

    public static RenderType overlayXray() {
        return OVERLAY_XRAY;
    }
}
