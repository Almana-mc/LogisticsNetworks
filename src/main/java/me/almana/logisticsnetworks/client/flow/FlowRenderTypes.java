package me.almana.logisticsnetworks.client.flow;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class FlowRenderTypes {
    private static final RenderPipeline DEPTH = pipeline("flow_lines", false);
    private static final RenderPipeline XRAY = pipeline("flow_lines_xray", true);
    private static final RenderType BASE_DEPTH = type("flow_lines", DEPTH);
    private static final RenderType BASE_XRAY = type("flow_lines_xray", XRAY);
    private static final RenderType PULSE_DEPTH = type("flow_pulses", DEPTH);
    private static final RenderType PULSE_XRAY = type("flow_pulses_xray", XRAY);

    private FlowRenderTypes() {
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(DEPTH);
        event.registerPipeline(XRAY);
    }

    static RenderType base(boolean throughBlocks) {
        return throughBlocks ? BASE_XRAY : BASE_DEPTH;
    }

    static RenderType pulse(boolean throughBlocks) {
        return throughBlocks ? PULSE_XRAY : PULSE_DEPTH;
    }

    private static RenderPipeline pipeline(String name, boolean throughBlocks) {
        return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "pipeline/" + name))
                .withDepthStencilState(throughBlocks ? Optional.empty()
                        : Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)))
                .build();
    }

    private static RenderType type(String name, RenderPipeline pipeline) {
        return RenderType.create("logisticsnetworks_" + name, RenderSetup.builder(pipeline).createRenderSetup());
    }
}
