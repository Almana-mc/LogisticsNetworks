package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.block.TwoCellBlock;
import me.almana.logisticsnetworks.render.NodeRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class TwoCellPlacementPreview {

    private static final float ALPHA = 0.4F;
    private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final RandomSource RANDOM = RandomSource.create();

    private TwoCellPlacementPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
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
        List<BlockStateModelPart> parts = new ArrayList<>();
        minecraft.getModelManager().getBlockStateModelSet().get(state)
                .collectParts(minecraft.level, pos, state, RANDOM, parts);
        CardinalLighting lighting = minecraft.level.cardinalLighting();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(NodeRenderTypes.overlay());
        PoseStack poseStack = event.getPoseStack();
        QuadInstance instance = new QuadInstance();

        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        PoseStack.Pose pose = poseStack.last();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    float shade = quad.materialInfo().shade() ? lighting.byFace(quad.direction()) : lighting.up();
                    instance.setColor(ARGB.colorFromFloat(ALPHA, shade, shade, shade));
                    consumer.putBakedQuad(pose, quad, instance);
                }
            }
        }
        poseStack.popPose();
        buffers.endBatch(NodeRenderTypes.overlay());
    }
}
