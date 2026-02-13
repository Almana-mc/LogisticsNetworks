package me.almana.logisticsnetworks.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class NodeModel<T extends Entity> extends EntityModel<T> {

        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "node"), "main");

        private final ModelPart mainBody;

        public NodeModel(ModelPart root) {
                this.mainBody = root.getChild("bb_main");
        }

        public static LayerDefinition createBodyLayer() {
                MeshDefinition mesh = new MeshDefinition();
                PartDefinition root = mesh.getRoot();

                // Main body definition
                PartDefinition main = root.addOrReplaceChild("bb_main",
                                CubeListBuilder.create()
                                                .texOffs(0, 0)
                                                .addBox(-8.0F, 0.0F, -8.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(0, 2)
                                                .addBox(-8.0F, 0.0F, -9.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(0, 6)
                                                .addBox(-8.0F, -1.0F, 8.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(0, 8)
                                                .addBox(-8.0F, 0.0F, 7.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                                                .texOffs(0, 10)
                                                .addBox(-8.0F, 0.0F, 8.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                                                .texOffs(0, 12)
                                                .addBox(-8.0F, -1.0F, -9.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(34, 16)
                                                .addBox(-7.0F, -15.0F, -9.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(34, 18)
                                                .addBox(-7.0F, -15.0F, 8.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(0, 32)
                                                .addBox(-8.0F, -15.0F, -8.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(0, 34)
                                                .addBox(-8.0F, -15.0F, 7.0F, 16.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(34, 20)
                                                .addBox(-7.0F, -14.0F, -9.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F))
                                                .texOffs(34, 22).addBox(-7.0F, -14.0F, 8.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F)),
                                PartPose.offset(0.0F, 24.0F, 0.0F));

                addRotatedParts(main);

                return LayerDefinition.create(mesh, 64, 64);
        }

        private static void addRotatedParts(PartDefinition parent) {
                parent.addOrReplaceChild("cube_r1",
                                CubeListBuilder.create().texOffs(34, 34).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(7.0F, -14.0F, 7.0F, 0.0F, 1.5708F, 0.0F));

                parent.addOrReplaceChild("cube_r2",
                                CubeListBuilder.create().texOffs(34, 32)
                                                .addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                                                .texOffs(34, 30).addBox(0.0F, -2.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(8.0F, -13.0F, 7.0F, 0.0F, 1.5708F, 0.0F));

                parent.addOrReplaceChild("cube_r3",
                                CubeListBuilder.create().texOffs(34, 28).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, -14.0F, 7.0F, 0.0F, 1.5708F, 0.0F));

                parent.addOrReplaceChild("cube_r4",
                                CubeListBuilder.create().texOffs(34, 26)
                                                .addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                                                .texOffs(34, 24).addBox(0.0F, -2.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-9.0F, -13.0F, 7.0F, 0.0F, 1.5708F, 0.0F));

                parent.addOrReplaceChild("cube_r5",
                                CubeListBuilder.create().texOffs(34, 14).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(9.0F, -15.0F, 7.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r6",
                                CubeListBuilder.create().texOffs(34, 12).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(8.0F, -15.0F, 8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r7",
                                CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(9.0F, -15.0F, 8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r8",
                                CubeListBuilder.create().texOffs(34, 10).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-7.0F, -15.0F, 8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r9",
                                CubeListBuilder.create().texOffs(34, 8).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, -15.0F, 7.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r10",
                                CubeListBuilder.create().texOffs(0, 28).addBox(0.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, -15.0F, 8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r11",
                                CubeListBuilder.create().texOffs(34, 6).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, -15.0F, -8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r12",
                                CubeListBuilder.create().texOffs(34, 4).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-7.0F, -15.0F, -9.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r13",
                                CubeListBuilder.create().texOffs(0, 26).addBox(0.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, -15.0F, -9.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r14",
                                CubeListBuilder.create().texOffs(34, 2).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(8.0F, -15.0F, -9.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r15",
                                CubeListBuilder.create().texOffs(34, 0).addBox(0.0F, -1.0F, 0.0F, 14.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(9.0F, -15.0F, -8.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r16",
                                CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(9.0F, -15.0F, -9.0F, -1.5708F, 0.0F, 1.5708F));
                parent.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(0, 22)
                                .addBox(-1.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(0, 18)
                                .addBox(-1.0F, 0.0F, 0.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(9.0F, 0.0F, -7.0F, 0.0F, -1.5708F, 0.0F));
                parent.addOrReplaceChild("cube_r18",
                                CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(8.0F, 1.0F, -7.0F, 0.0F, -1.5708F, 0.0F));
                parent.addOrReplaceChild("cube_r19",
                                CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F,
                                                new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-7.0F, 1.0F, -7.0F, 0.0F, -1.5708F, 0.0F));
                parent.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(0, 14)
                                .addBox(-1.0F, -1.0F, 0.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(0, 4)
                                .addBox(-1.0F, -2.0F, 0.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                                PartPose.offsetAndRotation(-8.0F, 1.0F, -7.0F, 0.0F, -1.5708F, 0.0F));
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                        float headPitch) {
                // No animation for now..?
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                        int color) {
                mainBody.render(poseStack, buffer, packedLight, packedOverlay, color);
        }
}
