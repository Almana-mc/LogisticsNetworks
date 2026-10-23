package me.almana.logisticsnetworks.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.RenderType;

public class NodeModel<T extends Entity> extends EntityModel<T> {

        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
                        ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "node"), "main");

        private final ModelPart mainBody;

        public NodeModel(ModelPart root) {
                super(RenderType::entityCutoutNoCull);
                this.mainBody = root.getChild("bb_main");
        }

        public static LayerDefinition createBodyLayer() {
                MeshDefinition mesh = new MeshDefinition();
                mesh.getRoot().addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 0)
                                .addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)),
                                PartPose.offset(0.0F, 24.0F, 0.0F));
                return LayerDefinition.create(mesh, 64, 64);
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
