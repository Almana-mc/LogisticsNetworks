package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class FlowAdvancedTab implements WrenchTab {
    static final String PREFIX = "gui.logisticsnetworks.wrench.flow.";
    private static final ChannelType[] TYPES = ChannelType.values();
    private static final int LEFT_W = 150;
    private static final int FIELD_X = 92;
    private static final int FIELD_W = 58;
    private static final int ROW = 16;
    private static final int ROW_H = 12;
    private static final int RIGHT_X = 164;
    private static final int RIGHT_W = 126;
    private static final int SWATCH_W = 22;
    private static final int SWATCH_H = 14;

    private final ColorPicker picker = new ColorPicker(RIGHT_W, 50);
    private final int[] colors = new int[TYPES.length];
    private final List<NumberField> numbers;
    WrenchFlow.Style style;
    boolean pulses;
    boolean throughBlocks;
    private int target;
    private int x;
    private int y;

    FlowAdvancedTab(WrenchFlow flow) {
        numbers = List.of(
                new NumberField("thickness", 1, WrenchFlow.THICKNESS, box("thickness")),
                new NumberField("speed", 2, WrenchFlow.SPEED, box("speed")),
                new NumberField("opacity", 3, WrenchFlow.OPACITY, box("opacity")),
                new NumberField("pulseSpacing", 5, WrenchFlow.PULSE_SPACING, box("pulseSpacing")),
                new NumberField("pulseLength", 6, WrenchFlow.PULSE_LENGTH, box("pulseLength")));
        load(flow);
    }

    private static EditBox box(String key) {
        EditBox box = new FlatEditBox(Minecraft.getInstance().font, 0, 0, FIELD_W - 8, ROW_H,
                Component.translatable(PREFIX + key));
        box.setMaxLength(8);
        box.setFilter(text -> text.matches("[0-9]*\\.?[0-9]*"));
        box.setTextColor(ThemeState.active().text());
        return box;
    }

    private void load(WrenchFlow flow) {
        style = flow.style();
        pulses = flow.pulses();
        throughBlocks = flow.throughBlocks();
        for (int i = 0; i < colors.length; i++) colors[i] = flow.colors().get(i);
        picker.load(colors[target]);
        double[] values = { flow.thickness(), flow.speed(), flow.opacity(), flow.pulseSpacing(), flow.pulseLength() };
        for (int i = 0; i < values.length; i++) numbers.get(i).box().setValue(Double.toString(values[i]));
    }

    @Override
    public void place(int x, int y) {
        this.x = x;
        this.y = y;
        for (NumberField number : numbers) number.box().setPosition(x + FIELD_X + 4, rowY(number.row()));
        picker.place(x + RIGHT_X, y + SWATCH_H + 6);
    }

    private int rowY(int row) {
        return y + row * ROW;
    }

    private int swatchX(int index) {
        return x + RIGHT_X + index * (SWATCH_W + 4);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme) {
        label(g, font, "style", 0, theme.text());
        String styleName = Component.translatable(PREFIX + "style." + style.name().toLowerCase(Locale.ROOT)).getString();
        ThemePaint.button(g, font, x + FIELD_X, rowY(0), FIELD_W, ROW_H, "< " + styleName + " >",
                ColorPicker.inRect(mouseX, mouseY, x + FIELD_X, rowY(0), FIELD_W, ROW_H), theme);
        for (NumberField number : numbers) {
            label(g, font, number.key(), number.row(), number.valid() ? theme.text() : theme.danger());
            ThemePaint.sunkPanel(g, x + FIELD_X, rowY(number.row()), FIELD_W, ROW_H, theme);
            if (number.box().isFocused()) {
                g.renderOutline(x + FIELD_X, rowY(number.row()), FIELD_W, ROW_H, theme.accent());
            }
        }
        toggle(g, font, "pulses", 4, pulses, mouseX, mouseY, theme);
        toggle(g, font, "throughBlocks", 7, throughBlocks, mouseX, mouseY, theme);
        renderColours(g, font, mouseX, mouseY, theme);
    }

    private void label(GuiGraphics g, Font font, String key, int row, int color) {
        g.drawString(font, Component.translatable(PREFIX + key).getString(), x, rowY(row) + 2, color, false);
    }

    private void toggle(GuiGraphics g, Font font, String key, int row, boolean on, int mouseX, int mouseY, Theme theme) {
        ThemePaint.visibleToggle(g, font, x, rowY(row), LEFT_W, ROW_H, Component.translatable(PREFIX + key).getString(),
                on, ColorPicker.inRect(mouseX, mouseY, x, rowY(row), LEFT_W, ROW_H), theme);
    }

    private void renderColours(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme) {
        for (ChannelType type : TYPES) {
            int i = type.ordinal();
            g.fill(swatchX(i), y, swatchX(i) + SWATCH_W, y + SWATCH_H, 0xFF000000 | color(type));
            boolean hovered = ColorPicker.inRect(mouseX, mouseY, swatchX(i), y, SWATCH_W, SWATCH_H);
            g.renderOutline(swatchX(i), y, SWATCH_W, SWATCH_H,
                    i == target ? theme.accent() : hovered ? theme.borderStrong() : theme.border());
        }
        picker.render(g, font, theme);
        int lineY = picker.bottom() + 6;
        g.fill(x + RIGHT_X, lineY, x + RIGHT_X + RIGHT_W, lineY + 4, 0xFF000000 | picker.color());
        g.drawString(font, typeName(TYPES[target]), x + RIGHT_X, lineY + 8, theme.textMuted(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (ColorPicker.inRect(mouseX, mouseY, x + FIELD_X, rowY(0), FIELD_W, ROW_H)) style = style.next();
        else if (ColorPicker.inRect(mouseX, mouseY, x, rowY(4), LEFT_W, ROW_H)) pulses = !pulses;
        else if (ColorPicker.inRect(mouseX, mouseY, x, rowY(7), LEFT_W, ROW_H)) throughBlocks = !throughBlocks;
        else return selectSwatch(mouseX, mouseY) || picker.mouseClicked(mouseX, mouseY);
        return true;
    }

    private boolean selectSwatch(double mouseX, double mouseY) {
        for (int i = 0; i < TYPES.length; i++) {
            if (ColorPicker.inRect(mouseX, mouseY, swatchX(i), y, SWATCH_W, SWATCH_H)) {
                colors[target] = picker.color();
                target = i;
                picker.load(colors[i]);
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        load(WrenchFlow.DEFAULT);
    }

    @Override
    public ColorPicker picker() {
        return picker;
    }

    @Override
    public List<EditBox> fields() {
        return numbers.stream().map(NumberField::box).toList();
    }

    boolean valid() {
        return numbers.stream().allMatch(NumberField::valid);
    }

    double value(int index) {
        return numbers.get(index).value();
    }

    int color(ChannelType type) {
        return type.ordinal() == target ? picker.color() : colors[type.ordinal()];
    }

    List<Integer> colors() {
        colors[target] = picker.color();
        return Arrays.stream(colors).boxed().toList();
    }

    static String typeName(ChannelType type) {
        return Component.translatable("gui.logisticsnetworks.graph.channel." + type.name().toLowerCase(Locale.ROOT))
                .getString();
    }

    private record NumberField(String key, int row, WrenchFlow.Range range, EditBox box) {
        boolean valid() {
            try {
                return range.contains(Double.parseDouble(box.getValue()));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        double value() {
            return Double.parseDouble(box.getValue());
        }
    }
}
