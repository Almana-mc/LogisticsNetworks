package me.almana.logisticsnetworks.client.graph;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Arrays;

record GraphRenderState(Matrix3x2fc pose, float[] coordinates, int[] colors,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int index = 0; index < colors.length; index++) {
            consumer.addVertexWith2DPose(pose, coordinates[index * 2], coordinates[index * 2 + 1])
                    .setColor(colors[index]);
        }
    }

    static final class Builder {
        private float[] coordinates = new float[512];
        private int[] colors = new int[256];
        private int size;
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        void vertex(float x, float y, int color) {
            if (size == colors.length) {
                colors = Arrays.copyOf(colors, size * 2);
                coordinates = Arrays.copyOf(coordinates, size * 4);
            }
            coordinates[size * 2] = x;
            coordinates[size * 2 + 1] = y;
            colors[size++] = color;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        void submit(GuiGraphicsExtractor graphics) {
            if (size == 0) return;
            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            int left = (int) Math.floor(minX);
            int top = (int) Math.floor(minY);
            ScreenRectangle bounds = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left,
                    (int) Math.ceil(maxY) - top).transformMaxBounds(pose);
            ScreenRectangle scissor = graphics.peekScissorStack();
            if (scissor != null) bounds = scissor.intersection(bounds);
            if (bounds == null) return;
            graphics.submitGuiElementRenderState(new GraphRenderState(pose,
                    Arrays.copyOf(coordinates, size * 2), Arrays.copyOf(colors, size), scissor, bounds));
        }
    }
}
