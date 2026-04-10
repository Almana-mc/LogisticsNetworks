package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractScrollArea extends AbstractWidget {
    public static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 32;
    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("widget/scroller_background");
    private final AbstractScrollArea.ScrollbarSettings scrollbarSettings;
    private double scrollAmount;
    private boolean scrolling;

    public AbstractScrollArea(int x, int y, int width, int height, Component message, AbstractScrollArea.ScrollbarSettings scrollbarSettings) {
        super(x, y, width, height, message);
        this.scrollbarSettings = scrollbarSettings;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!this.visible) {
            return false;
        } else {
            this.setScrollAmount(this.scrollAmount() - scrollY * this.scrollRate());
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrolling) {
            if (event.y() < this.getY()) {
                this.setScrollAmount(0.0);
            } else if (event.y() > this.getBottom()) {
                this.setScrollAmount(this.maxScrollAmount());
            } else {
                double max = Math.max(1, this.maxScrollAmount());
                int barHeight = this.scrollerHeight();
                double yDragScale = Math.max(1.0, max / (this.height - barHeight));
                this.setScrollAmount(this.scrollAmount() + dy * yDragScale);
            }

            return true;
        } else {
            return super.mouseDragged(event, dx, dy);
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        this.scrolling = false;
    }

    public double scrollAmount() {
        return this.scrollAmount;
    }

    public void setScrollAmount(double scrollAmount) {
        this.scrollAmount = Mth.clamp(scrollAmount, 0.0, (double)this.maxScrollAmount());
    }

    public boolean updateScrolling(MouseButtonEvent event) {
        this.scrolling = this.scrollable() && this.isValidClickButton(event.buttonInfo()) && this.isOverScrollbar(event.x(), event.y());
        return this.scrolling;
    }

    protected boolean isOverScrollbar(double x, double y) {
        return x >= this.scrollBarX() && x <= this.scrollBarX() + this.scrollbarWidth() && y >= this.getY() && y < this.getBottom();
    }

    public void refreshScrollAmount() {
        this.setScrollAmount(this.scrollAmount);
    }

    public int maxScrollAmount() {
        return Math.max(0, this.contentHeight() - this.height);
    }

    protected boolean scrollable() {
        return this.maxScrollAmount() > 0;
    }

    public int scrollbarWidth() {
        return this.scrollbarSettings.scrollbarWidth();
    }

    protected int scrollerHeight() {
        return Mth.clamp((int)((float)(this.height * this.height) / this.contentHeight()), 32, this.height - 8);
    }

    protected int scrollBarX() {
        return this.getRight() - this.scrollbarWidth();
    }

    public int scrollBarY() {
        return this.maxScrollAmount() == 0
            ? this.getY()
            : Math.max(this.getY(), (int)this.scrollAmount * (this.height - this.scrollerHeight()) / this.maxScrollAmount() + this.getY());
    }

    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int scrollbarX = this.scrollBarX();
        int scrollerHeight = this.scrollerHeight();
        int scrollerY = this.scrollBarY();
        if (!this.scrollable() && this.scrollbarSettings.disabledScrollerSprite() != null) {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED, this.scrollbarSettings.backgroundSprite(), scrollbarX, this.getY(), this.scrollbarWidth(), this.getHeight()
            );
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED, this.scrollbarSettings.disabledScrollerSprite(), scrollbarX, this.getY(), this.scrollbarWidth(), scrollerHeight
            );
            if (this.isOverScrollbar(mouseX, mouseY)) {
                graphics.requestCursor(CursorTypes.NOT_ALLOWED);
            }
        }

        if (this.scrollable()) {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED, this.scrollbarSettings.backgroundSprite(), scrollbarX, this.getY(), this.scrollbarWidth(), this.getHeight()
            );
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED, this.scrollbarSettings.scrollerSprite(), scrollbarX, scrollerY, this.scrollbarWidth(), scrollerHeight
            );
            if (this.isOverScrollbar(mouseX, mouseY)) {
                graphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
            }
        }
    }

    protected abstract int contentHeight();

    protected double scrollRate() {
        return this.scrollbarSettings.scrollRate();
    }

    public static AbstractScrollArea.ScrollbarSettings defaultSettings(int scrollRate) {
        return new AbstractScrollArea.ScrollbarSettings(SCROLLER_SPRITE, null, SCROLLER_BACKGROUND_SPRITE, 6, 32, scrollRate, true);
    }

    @OnlyIn(Dist.CLIENT)
    public record ScrollbarSettings(
        Identifier scrollerSprite,
        @Nullable Identifier disabledScrollerSprite,
        Identifier backgroundSprite,
        int scrollbarWidth,
        int scrollbarMinHeight,
        int scrollRate,
        boolean resizingScrollbar
    ) {
    }
}
