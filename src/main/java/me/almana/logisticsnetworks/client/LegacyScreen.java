package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * The plain-Screen counterpart of {@link LegacyContainerScreen}: it adapts the
 * 26.x extractRenderState / event-record shape back to the older render and
 * raw-argument one the screens are written against.
 */
public abstract class LegacyScreen extends Screen {
    protected LegacyScreen(Component title) {
        super(title);
    }

    protected void renderSuper(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <26 {
        /*super.render(graphics, mouseX, mouseY, partialTick);
        *///?} else {
        super.extractRenderState(graphics.raw(), mouseX, mouseY, partialTick);
        //?}
    }

    //? if >=26 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        render(new GuiGraphics(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (mouseDragged(event.x(), event.y(), event.button(), dx, dy)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (keyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (charTyped((char) event.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(event);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    //?}
}
