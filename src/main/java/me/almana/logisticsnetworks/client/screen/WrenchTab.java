package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

interface WrenchTab {
    void place(int x, int y);

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme);

    boolean mouseClicked(double mouseX, double mouseY);

    void reset();

    default ColorPicker picker() {
        return null;
    }

    default List<EditBox> fields() {
        return List.of();
    }
}
