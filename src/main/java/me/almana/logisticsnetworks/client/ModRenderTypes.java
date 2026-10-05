package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

public class ModRenderTypes extends RenderStateShard {

    public static final RenderType SELECTION_LINES = RenderType.create("logisticsnetworks_selection_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static double flowWidth = 6;
    private static boolean flowThroughBlocks = true;
    public static RenderType FLOW_LINES = flowLines("logisticsnetworks_flow_lines", flowWidth, flowThroughBlocks);
    public static RenderType FLOW_PULSES = flowLines("logisticsnetworks_flow_pulses", flowWidth * 1.5,
            flowThroughBlocks);

    public static void refreshFlowState(double width, boolean throughBlocks) {
        if (flowWidth == width && flowThroughBlocks == throughBlocks) return;
        flowWidth = width;
        flowThroughBlocks = throughBlocks;
        FLOW_LINES = flowLines("logisticsnetworks_flow_lines", flowWidth, flowThroughBlocks);
        FLOW_PULSES = flowLines("logisticsnetworks_flow_pulses", flowWidth * 1.5, flowThroughBlocks);
    }

    private static RenderType flowLines(String name, double width, boolean throughBlocks) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(width)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(throughBlocks ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    private ModRenderTypes() {
        super("mod_render_types", () -> {
        }, () -> {
        });
    }

    public static final RenderType OVERLAY = RenderType.create(
            "logisticsnetworks_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    public static final RenderType OVERLAY_XRAY = RenderType.create(
            "logisticsnetworks_overlay_xray",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
