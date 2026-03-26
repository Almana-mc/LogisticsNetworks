package me.almana.logisticsnetworks.render;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class NodeModel extends EntityModel<LogisticsNodeRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "node"), "main");

    private static final String MODEL_PATH = "/assets/logisticsnetworks/models/entity/node.json";

    public NodeModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public static LayerDefinition createBodyLayer() {
        return NodeModelJson.loadLayerDefinition(MODEL_PATH);
    }

    @Override
    public void setupAnim(LogisticsNodeRenderState state) {
    }
}
