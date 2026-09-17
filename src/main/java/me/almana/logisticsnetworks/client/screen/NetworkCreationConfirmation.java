package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import net.minecraft.client.gui.Font;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class NetworkCreationConfirmation {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 72;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_Y = 46;

    private final Font font;
    private final Component message;
    private final int x;
    private final int y;
    private final Runnable create;
    private final Runnable close;

    private NetworkCreationConfirmation(Font font, int width, int height, String name,
                                        Runnable create, Runnable close) {
        this.font = font;
        this.message = Component.translatable("gui.logisticsnetworks.node.confirm_network_creation", name);
        this.x = (width - WIDTH) / 2;
        this.y = (height - HEIGHT) / 2;
        this.create = create;
        this.close = close;
    }

    static NetworkCreationConfirmation open(boolean enabled, Font font, int width, int height, String name,
                                            Runnable create, Runnable close) {
        if (!enabled) {
            create.run();
            return null;
        }
        return new NetworkCreationConfirmation(font, width, height, name, create, close);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, Theme theme) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        ThemePaint.window(graphics, x, y, WIDTH, HEIGHT, theme);
        graphics.drawWordWrap(font, message, x + 10, y + 12, WIDTH - 20, theme.text());
        ThemePaint.button(graphics, font, x + 10, y + BUTTON_Y, BUTTON_WIDTH, 18,
                Component.translatable("gui.cancel").getString(), inside(mouseX, mouseY, x + 10), theme);
        ThemePaint.button(graphics, font, x + 114, y + BUTTON_Y, BUTTON_WIDTH, 18,
                Component.translatable("gui.logisticsnetworks.node.network_create").getString(),
                inside(mouseX, mouseY, x + 114), theme);
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
        create.run();
        close.run();
    }

    private boolean inside(double mouseX, double mouseY, int buttonX) {
        return mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                && mouseY >= y + BUTTON_Y && mouseY < y + BUTTON_Y + 18;
    }
}
