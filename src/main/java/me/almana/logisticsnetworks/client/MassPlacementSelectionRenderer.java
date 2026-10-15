package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.integration.iris.IrisCompat;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public final class MassPlacementSelectionRenderer {

    private static final float OUTLINE_RED = 0.2F;
    private static final float OUTLINE_GREEN = 0.87F;
    private static final float OUTLINE_BLUE = 0.33F;
    private static final float OUTLINE_ALPHA = 1.0F;
    private static final double OUTLINE_INFLATE = 0.004D;
    private static final double OUTLINE_STEP = 0.006D;
    private static final int OUTLINE_PASSES = 4;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private MassPlacementSelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        var renderStage = IrisCompat.isLoaded() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (event.getStage() != renderStage || IrisCompat.isRenderingShadowPass()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ItemStack wrenchStack = getMassPlacementWrench(player);
        if (wrenchStack.isEmpty()) {
            return;
        }

        WrenchItem.MassSelectionArea area = WrenchItem.getMassSelectionArea(wrenchStack,
                player.level().dimension());
        if (area == null) {
            return;
        }

        renderSelection(event, area);
    }

    private static void renderSelection(RenderLevelStageEvent event, WrenchItem.MassSelectionArea area) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderType renderType = IrisCompat.isLoaded() ? ModRenderTypes.SELECTION_LINES : RenderType.lines();

        AABB baseBox = new AABB(area.min().getX(), area.min().getY(), area.min().getZ(),
                area.max().getX() + 1, area.max().getY() + 1, area.max().getZ() + 1);
        if (baseBox.getCenter().distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) return;
        bufferSource.endLastBatch();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        poseStack.pushPose();
        try {
            if (IrisCompat.isLoaded()) {
                minecraft.getMainRenderTarget().bindWrite(false);
                ModRenderTypes.levelView(poseStack, event.getCamera());
            }
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            renderOutline(poseStack, bufferSource.getBuffer(renderType), baseBox);
            bufferSource.endBatch(renderType);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            poseStack.popPose();
        }
    }

    private static void renderOutline(PoseStack poseStack, VertexConsumer consumer, AABB baseBox) {
        for (int i = 0; i < OUTLINE_PASSES; i++) {
            AABB box = baseBox.inflate(OUTLINE_INFLATE + OUTLINE_STEP * i);
            LevelRenderer.renderLineBox(poseStack, consumer, box,
                    OUTLINE_RED, OUTLINE_GREEN, OUTLINE_BLUE, OUTLINE_ALPHA);
        }
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
