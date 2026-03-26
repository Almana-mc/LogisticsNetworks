package me.almana.logisticsnetworks.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.RenderType;

public class NodeModel<T extends Entity> extends EntityModel<T> {

        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
                        Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "node"), "main");

        private static final String MODEL_PATH = "/assets/logisticsnetworks/models/entity/node.json";

        private final ModelPart mainBody;

        public NodeModel(ModelPart root) {
                super(RenderType::entityCutoutNoCull);
                this.mainBody = root.getChild("bb_main");
        }

        public static LayerDefinition createBodyLayer() {
                return JsonModelLoader.loadLayerDefinition(MODEL_PATH);
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                        float headPitch) {
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                        int color) {
                mainBody.render(poseStack, buffer, packedLight, packedOverlay, color);
        }
}
