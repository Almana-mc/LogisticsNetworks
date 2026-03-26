package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.Logisticsnetworks;
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

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public final class MassPlacementSelectionRenderer {

    private static final float OUTLINE_RED = 1.0F;
    private static final float OUTLINE_GREEN = 0.55F;
    private static final float OUTLINE_BLUE = 0.0F;
    private static final float OUTLINE_ALPHA = 1.0F;
    private static final double OUTLINE_INFLATE = 0.002D;
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

        var selections = WrenchItem.getMassSelections(wrenchStack, player.level().dimension());
        if (selections.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());

        for (WrenchItem.MassSelectionTarget selection : selections) {
            double centerX = selection.pos().getX() + 0.5D;
            double centerY = selection.pos().getY() + 0.5D;
            double centerZ = selection.pos().getZ() + 0.5D;
            double dx = centerX - cameraPos.x;
            double dy = centerY - cameraPos.y;
            double dz = centerZ - cameraPos.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            AABB box = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D).inflate(OUTLINE_INFLATE);
            Vec3 offset = Vec3.atLowerCornerOf(selection.pos()).subtract(cameraPos);

            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(box),
                    0.0D, 0.0D, 0.0D, 0xFFFF8C00, OUTLINE_ALPHA);
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
