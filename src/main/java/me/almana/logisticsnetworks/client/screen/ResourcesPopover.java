package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.theme.ChannelTint;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ResourcesPopover {

    interface Target {
        int types();
        void setTypes(int mask);
        int available();
        int batch(ChannelType type);
        void setBatch(ChannelType type, int value);
        int cap(ChannelType type);
        int recipeSets();
        void setRecipeSets(int value);
        boolean exporting();
        boolean hasCard();
        void commit();
    }

    static final int W = 164;
    private static final int ROW_H = 13;
    private static final int HEADER_H = 15;
    private static final int SETS_ROW = ChannelType.values().length;
    private static final int MAX_SETS = 1024;

    private final Font font;
    private Target target;
    private boolean open;
    private int x;
    private int y;
    private EditBox edit;
    private int editRow = -1;
    private long lastClick;
    private int lastClickRow = -1;

    ResourcesPopover(Font font) {
        this.font = font;
    }

    boolean isOpen() {
        return open;
    }

    void open(Target target, int x, int y) {
        this.target = target;
        this.x = x;
        this.y = y;
        open = true;
    }

    void close() {
        stopEdit(false);
        open = false;
    }

    void render(GuiGraphics g, Theme t, int mx, int my) {
        if (!open) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + W + 1, y + height() + 1, t.borderStrong());
        g.fill(x, y, x + W, y + height(), t.surface());
        g.drawString(font, ResourceLabels.tr("title"), x + 4, y + 4, t.textMuted(), false);
        ThemePaint.button(g, font, x + W - 60, y + 2, 27, 11, ResourceLabels.tr("all"), inside(mx, my, x + W - 60, y + 2, 27, 11), t);
        ThemePaint.button(g, font, x + W - 31, y + 2, 27, 11, ResourceLabels.tr("none"), inside(mx, my, x + W - 31, y + 2, 27, 11), t);
        for (int row = 0; row < rows(); row++) {
            renderRow(g, t, row, y + HEADER_H + row * ROW_H, mx, my);
        }
        if (edit != null) edit.render(g, mx, my, 0);
        g.pose().popPose();
    }

    // True when click consumed
    boolean click(double mx, double my, int action) {
        if (!open) return false;
        if (edit != null && edit.isMouseOver(mx, my)) {
            edit.mouseClicked(mx, my, 0);
            return true;
        }
        stopEdit(true);
        if (!inside(mx, my, x, y, W, height())) {
            close();
            return true;
        }
        if (inside(mx, my, x + W - 60, y + 2, 56, 11)) {
            ClickSound.play();
            target.setTypes(mx < x + W - 31 ? ChannelType.ALL : 0);
            target.commit();
            return true;
        }
        int row = (int) (my - y - HEADER_H) / ROW_H;
        if (my < y + HEADER_H || row >= rows()) return true;
        ClickSound.play();
        if (mx >= x + W - 64) {
            if (editable(row)) amountClick(row, action);
        } else if (row < SETS_ROW) {
            target.setTypes(target.types() ^ ChannelType.values()[row].bit());
        }
        target.commit();
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (!open) return false;
        if (edit != null) {
            if (key == 257 || key == 335) stopEdit(true);
            else if (key == 256) stopEdit(false);
            else edit.keyPressed(key, scan, modifiers);
            return true;
        }
        if (key == 256) {
            close();
            return true;
        }
        return false;
    }

    boolean charTyped(char c, int modifiers) {
        if (edit == null) return false;
        if (ArithmeticExpression.accepts(c)) edit.charTyped(c, modifiers);
        return true;
    }

    private void renderRow(GuiGraphics g, Theme t, int row, int ry, int mx, int my) {
        if (inside(mx, my, x, ry, W, ROW_H)) g.fill(x, ry, x + W, ry + ROW_H, 0x22FFFFFF);
        if (row == SETS_ROW) {
            g.drawString(font, ResourceLabels.tr("recipe_sets"), x + 20, ry + 3, t.text(), false);
            if (editRow != row) chip(g, t, amountLabel(row), ry);
            return;
        }
        ChannelType type = ChannelType.values()[row];
        boolean on = type.in(target.types());
        boolean locked = !type.in(target.available());
        g.fill(x + 4, ry + 3, x + 11, ry + 10, t.borderStrong());
        g.fill(x + 5, ry + 4, x + 10, ry + 9, on ? t.accent() : t.surfaceSunken());
        g.fill(x + 14, ry + 4, x + 17, ry + 9, ChannelTint.hue(type));
        g.drawString(font, ResourceLabels.typeName(type), x + 20, ry + 3, on && !locked ? t.text() : t.textSubtle(), false);
        if (locked) {
            String reason = font.plainSubstrByWidth(ResourceLabels.lockReason(type), W - 70);
            g.drawString(font, reason, x + W - 4 - font.width(reason), ry + 3, t.textSubtle(), false);
        } else if (target.exporting() && editRow != row) {
            chip(g, t, amountLabel(row), ry);
        }
    }

    private void chip(GuiGraphics g, Theme t, String label, int ry) {
        int w = font.width(label) + 8;
        int cx = x + W - 4 - w;
        g.fill(cx, ry + 2, cx + w, ry + 11, t.surfaceSunken());
        g.drawString(font, label, cx + 4, ry + 3, t.text(), false);
    }

    private void amountClick(int row, int action) {
        long now = System.currentTimeMillis();
        boolean twice = row == lastClickRow && now - lastClick < 250;
        lastClick = now;
        lastClickRow = row;
        if (twice) {
            startEdit(row);
            return;
        }
        if (ClientControls.modifier3Down()) {
            store(row, action == 0 ? cap(row) : 1);
            return;
        }
        int current = value(row) <= 0 ? cap(row) : value(row);
        store(row, current + (action == 0 ? 1 : -1) * (ClientControls.modifier1Down() ? 8 : 1));
    }

    private void startEdit(int row) {
        int ry = y + HEADER_H + row * ROW_H;
        edit = new EditBox(font, x + W - 66, ry + 1, 62, 11, Component.empty());
        edit.setMaxLength(32);
        edit.setValue(String.valueOf(value(row) <= 0 ? cap(row) : value(row)));
        edit.setFocused(true);
        editRow = row;
    }

    private void stopEdit(boolean save) {
        if (edit == null) return;
        if (save) {
            try {
                store(editRow, ArithmeticExpression.evaluate(edit.getValue().trim()));
                target.commit();
            } catch (NumberFormatException ignored) {
            }
        }
        edit = null;
        editRow = -1;
    }

    // At or above cap = Max
    private void store(int row, int value) {
        int stored = value >= cap(row) ? 0 : Math.max(1, value);
        if (row == SETS_ROW) target.setRecipeSets(stored);
        else target.setBatch(ChannelType.values()[row], stored);
    }

    private boolean editable(int row) {
        if (row == SETS_ROW) return true;
        ChannelType type = ChannelType.values()[row];
        return target.exporting() && type != ChannelType.ENERGY && type.in(target.available());
    }

    private int value(int row) {
        return row == SETS_ROW ? target.recipeSets() : target.batch(ChannelType.values()[row]);
    }

    private int cap(int row) {
        return row == SETS_ROW ? MAX_SETS : target.cap(ChannelType.values()[row]);
    }

    private String amountLabel(int row) {
        int value = value(row);
        if (value <= 0 || row < SETS_ROW && ChannelType.values()[row] == ChannelType.ENERGY) return ResourceLabels.tr("max");
        if (row == SETS_ROW) return String.valueOf(value);
        return switch (ChannelType.values()[row]) {
            case FLUID -> Component.translatable("gui.logisticsnetworks.node.value.batch.fluid", value).getString();
            case CHEMICAL -> Component.translatable("gui.logisticsnetworks.node.value.batch.chemical", value)
                    .getString();
            case SOURCE -> Component.translatable("gui.logisticsnetworks.node.value.batch.source", value).getString();
            default -> String.valueOf(value);
        };
    }

    private int rows() {
        return target.exporting() && target.hasCard() ? SETS_ROW + 1 : SETS_ROW;
    }

    private int height() {
        return HEADER_H + rows() * ROW_H + 2;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
