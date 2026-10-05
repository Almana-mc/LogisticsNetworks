package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.data.NetworkColors;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.SetWrenchColorsPayload;
import me.almana.logisticsnetworks.network.SetWrenchFlowPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Predicate;

public class WrenchSettingsScreen extends Screen {
    private static final int PAD = 10;
    private static final int W = 310;
    private static final int H = 224;
    private static final int TAB_H = 14;
    private static final int TOP_Y = 24;
    private static final int TOP_W = (W - 2 * PAD - 4) / 2;
    private static final int INNER_Y = 44;
    private static final int INNER_W = 70;
    private static final int BUTTON_H = 12;
    private static final String[] TABS = {
            "gui.logisticsnetworks.wrench.tab.colors", "gui.logisticsnetworks.wrench.tab.flow" };
    private static final String[] FLOW_TABS = {
            "gui.logisticsnetworks.wrench.flow.simple", "gui.logisticsnetworks.wrench.flow.advanced" };
    private static final String[] BUTTONS = {
            "gui.logisticsnetworks.node.color.random",
            "gui.logisticsnetworks.wrench.colors.reset",
            "gui.logisticsnetworks.node.color.apply" };

    private final InteractionHand hand;
    private final WrenchColoursTab colours;
    private final FlowAdvancedTab advanced;
    private final FlowSimpleTab simple;
    private int x;
    private int y;
    private int tab;
    private int flowTab;

    public WrenchSettingsScreen(ItemStack wrench, InteractionHand hand) {
        super(Component.translatable("gui.logisticsnetworks.wrench.settings.title"));
        this.hand = hand;
        WrenchFlow flow = WrenchItem.getFlow(wrench);
        colours = new WrenchColoursTab(WrenchItem.getCaseColor(wrench), WrenchItem.getScreenColor(wrench));
        advanced = new FlowAdvancedTab(flow);
        simple = new FlowSimpleTab(flow, advanced);
    }

    private WrenchTab active() {
        if (tab == 0) return colours;
        return flowTab == 0 ? simple : advanced;
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        colours.place(x + 50, y + TOP_Y + TAB_H + 8);
        simple.place(x + PAD, y + INNER_Y + TAB_H + 8);
        advanced.place(x + PAD, y + INNER_Y + TAB_H + 8);
        showFields();
    }

    private void showFields() {
        clearWidgets();
        blurPickers();
        active().fields().forEach(this::addRenderableWidget);
    }

    private void blurPickers() {
        colours.picker().blur();
        advanced.picker().blur();
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !(minecraft.player.getItemInHand(hand).getItem() instanceof WrenchItem)) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme theme = ThemeState.active();
        g.fill(0, 0, width, height, theme.bg());
        ThemePaint.window(g, x, y, W, H, theme);
        ThemePaint.drawCentered(g, font, title, x + W / 2, y + PAD, theme.accent());
        renderTabs(g, mouseX, mouseY, theme, TABS, y + TOP_Y, TOP_W, tab);
        if (tab == 1) renderTabs(g, mouseX, mouseY, theme, FLOW_TABS, y + INNER_Y, INNER_W, flowTab);
        active().render(g, font, mouseX, mouseY, theme);
        for (int i = 0; i < BUTTONS.length; i++) {
            String label = Component.translatable(BUTTONS[i]).getString();
            if (enabled(i)) {
                ThemePaint.button(g, font, buttonX(i), buttonY(), buttonW(), BUTTON_H, label,
                        onButton(mouseX, mouseY, i), theme);
            } else {
                ThemePaint.ghostButton(g, font, buttonX(i), buttonY(), buttonW(), BUTTON_H, label, false, theme);
            }
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY, Theme theme, String[] labels, int tabY, int tabW,
                            int selected) {
        for (int i = 0; i < labels.length; i++) {
            int tx = x + PAD + i * (tabW + 4);
            boolean hovered = ColorPicker.inRect(mouseX, mouseY, tx, tabY, tabW, TAB_H);
            ThemePaint.button(g, font, tx, tabY, tabW, TAB_H, Component.translatable(labels[i]).getString(),
                    hovered || selected == i, theme);
            if (selected == i) g.renderOutline(tx, tabY, tabW, TAB_H, theme.accent());
        }
    }

    private int clickedTab(double mouseX, double mouseY, int count, int tabY, int tabW) {
        for (int i = 0; i < count; i++) {
            if (ColorPicker.inRect(mouseX, mouseY, x + PAD + i * (tabW + 4), tabY, tabW, TAB_H)) return i;
        }
        return -1;
    }

    private boolean enabled(int button) {
        if (button == 0) return active().picker() != null;
        return button != 2 || advanced.valid();
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
        if (button != 0) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            blurPickers();
            return true;
        }
        setFocused(null);
        int top = clickedTab(mouseX, mouseY, TABS.length, y + TOP_Y, TOP_W);
        int inner = tab == 1 ? clickedTab(mouseX, mouseY, FLOW_TABS.length, y + INNER_Y, INNER_W) : -1;
        if (top >= 0) {
            tab = top;
            showFields();
        } else if (inner >= 0) {
            flowTab = inner;
            showFields();
        } else if (active().mouseClicked(mouseX, mouseY)) {
            return true;
        } else if (onButton(mouseX, mouseY, 0) && enabled(0)) {
            active().picker().load(NetworkColors.randomColor());
        } else if (onButton(mouseX, mouseY, 1)) {
            active().reset();
        } else if (onButton(mouseX, mouseY, 2) && enabled(2)) {
            apply();
        } else if (!ColorPicker.inRect(mouseX, mouseY, x, y, W, H)) {
            onClose();
        }
        return true;
    }

    private void apply() {
        int[] colors = colours.colors();
        boolean reset = colors[0] == WrenchItem.DEFAULT_CASE_COLOR && colors[1] == WrenchItem.DEFAULT_SCREEN_COLOR;
        PacketDistributor.sendToServer(new SetWrenchColorsPayload(hand.ordinal(), reset, colors[0], colors[1]));
        PacketDistributor.sendToServer(new SetWrenchFlowPayload(hand.ordinal(), new WrenchFlow(simple.enabled,
                simple.types, simple.channels, simple.network(), simple.offscreen, advanced.style, advanced.colors(),
                advanced.value(0), advanced.value(1), advanced.value(2), advanced.pulses, advanced.value(3),
                advanced.value(4), advanced.throughBlocks)));
        onClose();
    }

    private boolean onPicker(Predicate<ColorPicker> action) {
        ColorPicker picker = active().picker();
        return picker != null && action.test(picker);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return onPicker(picker -> picker.mouseDragged(mouseX, mouseY))
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onPicker(ColorPicker::mouseReleased) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (getFocused() instanceof EditBox) return super.charTyped(c, modifiers);
        return onPicker(picker -> picker.charTyped(c)) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox && (key == 256 || key == 257 || key == 335)) {
            setFocused(null);
            return true;
        }
        if (getFocused() instanceof EditBox) return super.keyPressed(key, scanCode, modifiers);
        return onPicker(picker -> picker.keyPressed(key)) || super.keyPressed(key, scanCode, modifiers);
    }
}
