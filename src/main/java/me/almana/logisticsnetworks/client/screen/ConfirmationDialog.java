package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ConfirmationDialog {
    private static final int WIDTH = 220;
    private static final int BUTTON_WIDTH = 96;

    private final Font font;
    private final Component message;
    private final String confirmText;
    private final int x;
    private final int y;
    private final int buttonY;
    private final int height;
    private final Runnable confirm;
    private final Runnable close;

    private ConfirmationDialog(Font font, int screenWidth, int screenHeight, Component message,
                               Component confirmText, Runnable confirm, Runnable close) {
        this.font = font;
        this.message = message;
        this.confirmText = confirmText.getString();
        this.buttonY = Math.max(46, 20 + font.split(message, WIDTH - 20).size() * font.lineHeight);
        this.height = buttonY + 26;
        this.x = (screenWidth - WIDTH) / 2;
        this.y = (screenHeight - height) / 2;
        this.confirm = confirm;
        this.close = close;
    }

    static ConfirmationDialog networkCreation(boolean enabled, Font font, int width, int height, String name,
                                              Runnable create, Runnable close) {
        if (!enabled) {
            create.run();
            return null;
        }
        return new ConfirmationDialog(font, width, height,
                Component.translatable("gui.logisticsnetworks.node.confirm_network_creation", name),
                Component.translatable("gui.logisticsnetworks.node.network_create"), create, close);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, Theme theme) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        ThemePaint.window(graphics, x, y, WIDTH, height, theme);
        graphics.drawWordWrap(font, message, x + 10, y + 12, WIDTH - 20, theme.text());
        ThemePaint.button(graphics, font, x + 10, y + buttonY, BUTTON_WIDTH, 18,
                Component.translatable("gui.cancel").getString(), inside(mouseX, mouseY, x + 10), theme);
        ThemePaint.button(graphics, font, x + 114, y + buttonY, BUTTON_WIDTH, 18,
                confirmText, inside(mouseX, mouseY, x + 114), theme);
        graphics.pose().popPose();
    }

    void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        if (inside(mouseX, mouseY, x + 10)) close.run();
        else if (inside(mouseX, mouseY, x + 114)) confirm();
    }

    void keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm();
    }

    private void confirm() {
        confirm.run();
        close.run();
    }

    private boolean inside(double mouseX, double mouseY, int buttonX) {
        return mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                && mouseY >= y + buttonY && mouseY < y + buttonY + 18;
    }
}
