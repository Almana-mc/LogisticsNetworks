package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.data.NetworkColors;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.SetWrenchColorsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class WrenchSettingsScreen extends Screen {
    private static final int PAD = 10;
    private static final int W = 230;
    private static final int H = 184;
    private static final int BUTTON_H = 12;
    private static final String[] BUTTONS = {
            "gui.logisticsnetworks.node.color.random",
            "gui.logisticsnetworks.wrench.colors.reset",
            "gui.logisticsnetworks.node.color.apply" };

    private final InteractionHand hand;
    private final WrenchColoursTab colours;
    private int x;
    private int y;

    public WrenchSettingsScreen(ItemStack wrench, InteractionHand hand) {
        super(Component.translatable("gui.logisticsnetworks.wrench.colors.title"));
        this.hand = hand;
        colours = new WrenchColoursTab(WrenchItem.getCaseColor(wrench), WrenchItem.getScreenColor(wrench));
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        colours.place(x + PAD, y + PAD + 10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Theme theme = ThemeState.active();
        g.fill(0, 0, width, height, theme.bg());
        ThemePaint.window(g, x, y, W, H, theme);
        ThemePaint.drawCentered(g, font, title, x + W / 2, y + PAD, theme.accent());
        colours.render(g, font, mouseX, mouseY, theme);
        for (int i = 0; i < BUTTONS.length; i++) {
            ThemePaint.button(g, font, buttonX(i), buttonY(), buttonW(), BUTTON_H,
                    Component.translatable(BUTTONS[i]).getString(), onButton(mouseX, mouseY, i), theme);
        }
    }

    private int buttonY() {
        return y + H - PAD - BUTTON_H;
    }

    private int buttonW() {
        return (W - 2 * PAD - 12) / 3;
    }

    private int buttonX(int index) {
        return x + PAD + index * (buttonW() + 6);
    }

    private boolean onButton(double mouseX, double mouseY, int index) {
        return ColorPicker.inRect(mouseX, mouseY, buttonX(index), buttonY(), buttonW(), BUTTON_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (colours.mouseClicked(mouseX, mouseY)) return true;
        if (onButton(mouseX, mouseY, 0)) colours.picker().load(NetworkColors.randomColor());
        else if (onButton(mouseX, mouseY, 1)) colours.reset();
        else if (onButton(mouseX, mouseY, 2)) apply();
        else if (!ColorPicker.inRect(mouseX, mouseY, x, y, W, H)) onClose();
        return true;
    }

    private void apply() {
        int[] colors = colours.colors();
        boolean reset = colors[0] == WrenchItem.DEFAULT_CASE_COLOR && colors[1] == WrenchItem.DEFAULT_SCREEN_COLOR;
        PacketDistributor.sendToServer(new SetWrenchColorsPayload(hand.ordinal(), reset, colors[0], colors[1]));
        onClose();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return colours.picker().mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return colours.picker().mouseReleased() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return colours.picker().charTyped(c) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return colours.picker().keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }
}
