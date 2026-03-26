package me.almana.logisticsnetworks.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public final class GuiGraphics {
    private final GuiGraphicsExtractor raw;
    private final Pose pose;

    public GuiGraphics(GuiGraphicsExtractor raw) {
        this.raw = raw;
        this.pose = new Pose(raw.pose(), raw);
    }

    public GuiGraphicsExtractor raw() {
        return raw;
    }

    public Pose pose() {
        return pose;
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        raw.fill(x0, y0, x1, y1, color);
    }

    public void renderOutline(int x, int y, int width, int height, int color) {
        raw.outline(x, y, width, height, color);
    }

    public void drawString(Font font, String text, int x, int y, int color) {
        raw.text(font, text, x, y, color, true);
    }

    public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        raw.text(font, text, x, y, color, shadow);
    }

    public void drawString(Font font, Component text, int x, int y, int color) {
        raw.text(font, text, x, y, color, true);
    }

    public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        raw.text(font, text, x, y, color, shadow);
    }

    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        raw.text(font, text, x, y, color, shadow);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        raw.centeredText(font, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        raw.centeredText(font, text, x, y, color);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        raw.item(stack, x, y);
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        raw.fakeItem(stack, x, y, 0);
    }

    public void renderTooltip(Font font, Component text, int x, int y) {
        raw.setTooltipForNextFrame(font, text, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> image, int x, int y) {
        raw.setTooltipForNextFrame(font, lines, image, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, int x, int y) {
        raw.setTooltipForNextFrame(font, lines, Optional.empty(), x, y);
    }

    public void blit(int x, int y, int z, int width, int height, TextureAtlasSprite sprite) {
        raw.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }

    public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(Identifier texture, int x, int y, int width, int height, float u0, float u1, float v0, float v1) {
        raw.blit(texture, x, y, x + width, y + height, u0, u1, v0, v1);
    }

    public static final class Pose {
        private final Matrix3x2fStack raw;
        private final GuiGraphicsExtractor graphics;

        private Pose(Matrix3x2fStack raw, GuiGraphicsExtractor graphics) {
            this.raw = raw;
            this.graphics = graphics;
        }

        public void pushPose() {
            raw.pushMatrix();
        }

        public void popPose() {
            raw.popMatrix();
        }

        public void translate(float x, float y, float z) {
            raw.translate(x, y);
            if (z > 0.0F) {
                graphics.nextStratum();
            }
        }

        public void scale(float x, float y, float z) {
            raw.scale(x, y);
        }
    }
}
