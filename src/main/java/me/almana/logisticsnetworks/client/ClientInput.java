package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
//? if >=26 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}

/**
 * 26.x wrapped the raw input arguments into KeyEvent / CharacterEvent /
 * MouseButtonEvent records and renamed Renderable's draw method. Screens
 * forward to their child widgets through here so they stay version-agnostic.
 */
public final class ClientInput {
    private ClientInput() {
    }

    public static boolean keyPressed(GuiEventListener target, int keyCode, int scanCode, int modifiers) {
        //? if <26 {
        /*return target.keyPressed(keyCode, scanCode, modifiers);
        *///?} else {
        return target.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
        //?}
    }

    public static boolean charTyped(GuiEventListener target, char codePoint) {
        //? if <26 {
        /*return target.charTyped(codePoint, 0);
        *///?} else {
        return target.charTyped(new CharacterEvent(codePoint));
        //?}
    }

    public static boolean mouseClicked(GuiEventListener target, double mouseX, double mouseY, int button) {
        //? if <26 {
        /*return target.mouseClicked(mouseX, mouseY, button);
        *///?} else {
        return target.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        //?}
    }

    public static boolean mouseScrolled(GuiEventListener target, double mouseX, double mouseY, double delta) {
        //? if forge {
        /*return target.mouseScrolled(mouseX, mouseY, delta);
        *///?} else {
        return target.mouseScrolled(mouseX, mouseY, 0.0, delta);
        //?}
    }

    public static void widget(GuiGraphics graphics, Renderable widget, int mouseX, int mouseY, float partialTick) {
        //? if <26 {
        /*widget.render(graphics, mouseX, mouseY, partialTick);
        *///?} else {
        widget.extractRenderState(graphics.raw(), mouseX, mouseY, partialTick);
        //?}
    }
}
