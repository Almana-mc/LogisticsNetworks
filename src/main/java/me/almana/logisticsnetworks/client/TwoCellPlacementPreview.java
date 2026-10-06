package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.block.TwoCellBlock;
import me.almana.logisticsnetworks.integration.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class TwoCellPlacementPreview {

    private static final float ALPHA = 0.4F;
    private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final RandomSource RANDOM = RandomSource.create();

    private TwoCellPlacementPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        var renderStage = IrisCompat.isLoaded() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (event.getStage() != renderStage || IrisCompat.isRenderingShadowPass()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPlaceContext context = placement(minecraft.player, hit);
        if (context == null) return;
        BlockState state = context.getItemInHand().getItem() instanceof BlockItem item
                ? item.getBlock().getStateForPlacement(context) : null;
        BlockPos pos = context.getClickedPos();
        if (state == null || !state.canSurvive(minecraft.level, pos)
                || !minecraft.level.isUnobstructed(state, pos, CollisionContext.of(minecraft.player))) return;

        render(event, minecraft, state, pos);
    }

    private static @Nullable BlockPlaceContext placement(LocalPlayer player, BlockHitResult hit) {
        if (!player.getAbilities().mayBuild) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TwoCellBlock) {
                BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
                return context.canPlace() ? context : null;
            }
        }
        return null;
    }

    private static void render(RenderLevelStageEvent event, Minecraft minecraft, BlockState state, BlockPos pos) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();

        buffers.endLastBatch();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        poseStack.pushPose();
        try {
            if (IrisCompat.isLoaded()) {
                minecraft.getMainRenderTarget().bindWrite(false);
                poseStack.mulPose(event.getModelViewMatrix());
            }
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.OVERLAY);
            PoseStack.Pose pose = poseStack.last();
            for (Direction side : SIDES) {
                for (BakedQuad quad : model.getQuads(state, side, RANDOM, ModelData.EMPTY, null)) {
                    float shade = minecraft.level.getShade(quad.getDirection(), quad.isShade());
                    consumer.putBulkData(pose, quad, shade, shade, shade, ALPHA, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                }
            }
            buffers.endBatch(ModRenderTypes.OVERLAY);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            poseStack.popPose();
        }
    }
}
