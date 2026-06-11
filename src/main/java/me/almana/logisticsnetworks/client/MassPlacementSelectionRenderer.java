package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class MassPlacementSelectionRenderer {

    private static final float OUTLINE_ALPHA = 1.0F;
    private static final double OUTLINE_INFLATE = 0.004D;
    private static final double OUTLINE_STEP = 0.006D;
    private static final int OUTLINE_PASSES = 4;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private MassPlacementSelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ItemStack wrenchStack = getMassPlacementWrench(player);
        if (wrenchStack.isEmpty()) {
            return;
        }

        WrenchItem.MassSelectionArea area = WrenchItem.getMassSelectionArea(wrenchStack, player.level().dimension());
        if (area == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());

        var min = area.min();
        var max = area.max();
        double centerX = (min.getX() + max.getX() + 1.0D) * 0.5D;
        double centerY = (min.getY() + max.getY() + 1.0D) * 0.5D;
        double centerZ = (min.getZ() + max.getZ() + 1.0D) * 0.5D;
        double dx = centerX - cameraPos.x;
        double dy = centerY - cameraPos.y;
        double dz = centerZ - cameraPos.z;
        if ((dx * dx) + (dy * dy) + (dz * dz) <= MAX_RENDER_DISTANCE_SQR) {
            AABB baseBox = new AABB(0.0D, 0.0D, 0.0D,
                    max.getX() - min.getX() + 1.0D,
                    max.getY() - min.getY() + 1.0D,
                    max.getZ() - min.getZ() + 1.0D);
            Vec3 offset = Vec3.atLowerCornerOf(min).subtract(cameraPos);

            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            for (int i = 0; i < OUTLINE_PASSES; i++) {
                AABB box = baseBox.inflate(OUTLINE_INFLATE + OUTLINE_STEP * i);
                ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(box),
                        0.0D, 0.0D, 0.0D, 0xFF33DD55, OUTLINE_ALPHA);
            }
            poseStack.popPose();
        }

        bufferSource.endBatch(RenderTypes.lines());
    }

    private static ItemStack getMassPlacementWrench(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof WrenchItem && WrenchItem.getMode(mainHand) == WrenchItem.Mode.MASS_PLACEMENT) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof WrenchItem && WrenchItem.getMode(offHand) == WrenchItem.Mode.MASS_PLACEMENT) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }
}
