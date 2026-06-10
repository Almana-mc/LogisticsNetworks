package me.almana.logisticsnetworks.render;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class NodeModel extends EntityModel<LogisticsNodeRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "node"), "main");

    private static final String MODEL_PATH = "/assets/logisticsnetworks/models/entity/node.json";

    private final ModelPart[] connectionParts;
    private final ModelPart[] eastParts;
    private final ModelPart[] westParts;
    private final ModelPart[] westTopParts;
    private final ModelPart[] eastCapParts;
    private final ModelPart[] westCapParts;
    private final ModelPart[] northParts;
    private final ModelPart[] southParts;
    private final ModelPart[] upParts;
    private final ModelPart[] downParts;

    public NodeModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        ModelPart main = root.getChild("bb_main");
        this.eastParts = parts(main, "east_lower_inner", "east_lower_outer", "east_upper_inner",
                "east_upper_outer");
        this.westParts = parts(main, "west_lower_inner", "west_lower_outer", "west_upper_inner",
                "west_upper_outer");
        this.westTopParts = parts(main, "west_top_back", "west_top_mid_back", "west_top_outer_back",
                "west_top_mid_front", "west_top_front", "west_top_outer_front");
        this.eastCapParts = caps(main, "east_cap", "north_bottom_inner", "north_bottom_outer",
                "south_bottom_inner", "south_bottom_mid", "south_bottom_outer", "north_bottom_outer_low",
                "north_top_outer", "south_top_outer", "north_top_inner", "south_top_inner",
                "north_top_outer_low", "south_top_mid");
        this.westCapParts = caps(main, "west_cap", "north_bottom_inner", "north_bottom_outer",
                "south_bottom_inner", "south_bottom_mid", "south_bottom_outer", "north_bottom_outer_low",
                "north_top_outer", "south_top_outer", "north_top_inner", "south_top_inner",
                "north_top_outer_low", "south_top_mid");
        this.northParts = parts(main, "north_bottom_inner", "north_bottom_outer", "north_bottom_outer_low",
                "north_top_inner", "north_top_outer", "north_top_outer_low", "east_top_front",
                "east_top_mid_front", "east_top_outer_front", "west_top_front", "west_top_mid_front",
                "west_top_outer_front");
        this.southParts = parts(main, "south_bottom_inner", "south_bottom_mid", "south_bottom_outer",
                "south_top_inner", "south_top_mid", "south_top_outer", "east_top_mid_back",
                "east_top_back", "east_top_outer_back", "west_top_back", "west_top_mid_back",
                "west_top_outer_back");
        this.upParts = parts(main, "north_top_inner", "north_top_outer", "north_top_outer_low",
                "south_top_inner", "south_top_mid", "south_top_outer", "east_upper_inner",
                "east_upper_outer", "west_upper_inner", "west_upper_outer", "east_top_front",
                "east_top_mid_front", "east_top_outer_front", "east_top_mid_back", "east_top_back",
                "east_top_outer_back", "west_top_back", "west_top_mid_back", "west_top_outer_back",
                "west_top_mid_front", "west_top_front", "west_top_outer_front");
        this.downParts = parts(main, "north_bottom_inner", "north_bottom_outer", "north_bottom_outer_low",
                "south_bottom_inner", "south_bottom_mid", "south_bottom_outer", "east_lower_inner",
                "east_lower_outer", "west_lower_inner", "west_lower_outer");
        this.connectionParts = parts(main, "north_bottom_inner", "north_bottom_outer", "south_bottom_inner",
                "south_bottom_mid", "south_bottom_outer", "north_bottom_outer_low", "north_top_outer",
                "south_top_outer", "north_top_inner", "south_top_inner", "east_lower_inner",
                "east_lower_outer", "west_lower_inner", "west_lower_outer", "east_top_front",
                "east_top_mid_front", "east_top_outer_front", "east_top_mid_back", "east_top_back",
                "east_top_outer_back", "west_top_back", "west_top_mid_back", "west_top_outer_back",
                "west_top_mid_front", "west_top_front", "west_top_outer_front", "east_upper_inner",
                "east_upper_outer", "west_upper_inner", "west_upper_outer", "north_top_outer_low",
                "south_top_mid");
    }

    public static LayerDefinition createBodyLayer() {
        return NodeModelJson.loadLayerDefinition(MODEL_PATH);
    }

    @Override
    public void setupAnim(LogisticsNodeRenderState state) {
        setVisible(connectionParts, true);
        setVisible(eastCapParts, true);
        setVisible(westCapParts, true);

        if (NodeConnectionMask.has(state.connections, Direction.EAST)) {
            setVisible(westParts, false);
            setVisible(westTopParts, false);
            setVisible(westCapParts, false);
        }
        if (NodeConnectionMask.has(state.connections, Direction.WEST)) {
            setVisible(eastParts, false);
            setVisible(eastCapParts, false);
        }
        if (NodeConnectionMask.has(state.connections, Direction.NORTH)) {
            setVisible(northParts, false);
        }
        if (NodeConnectionMask.has(state.connections, Direction.SOUTH)) {
            setVisible(southParts, false);
        }
        if (NodeConnectionMask.has(state.connections, Direction.UP)) {
            setVisible(upParts, false);
        }
        if (NodeConnectionMask.has(state.connections, Direction.DOWN)) {
            setVisible(downParts, false);
        }
    }

    private static ModelPart[] parts(ModelPart parent, String... names) {
        ModelPart[] parts = new ModelPart[names.length];
        for (int i = 0; i < names.length; i++) {
            parts[i] = parent.getChild(names[i]);
        }
        return parts;
    }

    private static ModelPart[] caps(ModelPart parent, String suffix, String... names) {
        ModelPart[] parts = new ModelPart[names.length];
        for (int i = 0; i < names.length; i++) {
            parts[i] = parent.getChild(names[i] + "_" + suffix);
        }
        return parts;
    }

    private static void setVisible(ModelPart[] parts, boolean visible) {
        for (ModelPart part : parts) {
            part.visible = visible;
        }
    }
}
