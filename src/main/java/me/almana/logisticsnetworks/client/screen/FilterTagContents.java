package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.client.ClientInput;
import net.minecraft.ChatFormatting;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import net.minecraft.client.gui.Font;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static me.almana.logisticsnetworks.client.screen.FilterTagPicker.drawButton;
import static me.almana.logisticsnetworks.client.screen.FilterTagPicker.renderMember;
import static me.almana.logisticsnetworks.client.screen.FilterTagPicker.tr;

final class FilterTagContents {
    private static final int CELL_SIZE = 22;
    private static Theme theme() { return ThemeState.active(); }
    private final Font font;
    private final FilterTargetType target;
    private final FilterTagCatalog.Entry tag;
    private final Runnable close;
    private final EditBox search;
    private List<FilterTagCatalog.Member> filtered;
    private Rect2i bounds;
    private Rect2i grid;
    private Rect2i back;
    private int columns;
    private int visibleRows;
    private int scroll;
    private int cursor;
    private int focus;
    private boolean draggingScrollbar;

    FilterTagContents(Font font, FilterTargetType target, FilterTagCatalog.Entry tag, Rect2i bounds, Runnable close) {
        this.font = font;
        this.target = target;
        this.tag = tag;
        this.close = close;
        filtered = FilterTagCatalog.searchMembers(tag.members(), "");
        search = new EditBox(font, 0, 0, 100, 16, tr("search"));
        search.setMaxLength(256);
        search.setHint(tr("contents.search." + target.getSerializedName()));
        search.setResponder(query -> refresh());
        search.setFocused(true);
        layout(bounds);
    }

    boolean isSearchFocused() {
        return search.isFocused();
    }

    private void layout(Rect2i area) {
        bounds = area;
        back = new Rect2i(area.getX() + 6, area.getY() + 6, 50, 14);
        search.setX(area.getX() + 46);
        search.setY(area.getY() + 37);
        search.setWidth(area.getWidth() - 52);
        columns = Math.max(1, (area.getWidth() - 22) / CELL_SIZE);
        visibleRows = Math.max(1, (area.getHeight() - 80) / CELL_SIZE);
        grid = new Rect2i(area.getX() + 6, area.getY() + 60, columns * CELL_SIZE, visibleRows * CELL_SIZE);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void refresh() {
        filtered = FilterTagCatalog.searchMembers(tag.members(), search.getValue());
        scroll = 0;
        cursor = 0;
        draggingScrollbar = false;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float delta, Rect2i area) {
        layout(area);
        int x = area.getX();
        int y = area.getY();
        int width = area.getWidth();
        graphics.fill(x, y, x + width, y + area.getHeight(), theme().surface());
        graphics.renderOutline(x, y, width, area.getHeight(), theme().accent());
        drawButton(graphics, font, back, tr("back"), true, focus == 2, mouseX, mouseY);
        graphics.drawString(font, font.plainSubstrByWidth(tag.name(), width - 68), x + 62, y + 9, theme().text(), false);
        graphics.drawString(font, font.plainSubstrByWidth("#" + tag.id(), width - 12), x + 6, y + 25, theme().textMuted(), false);
        graphics.drawString(font, tr("search"), x + 6, y + 41, theme().text(), false);
        search.extractRenderState(graphics.raw(), mouseX, mouseY, delta);
        renderGrid(graphics, mouseX, mouseY);
        graphics.drawString(font, tr("contents.matches", filtered.size(), tag.members().size()),
                x + 6, y + area.getHeight() - 15, theme().textMuted(), false);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = scroll * columns;
        int hovered = itemAt(mouseX, mouseY);
        for (int index = start; index < Math.min(filtered.size(), start + columns * visibleRows); index++) {
            int x = grid.getX() + index % columns * CELL_SIZE;
            int y = grid.getY() + (index / columns - scroll) * CELL_SIZE;
            boolean focused = focus == 1 && cursor == index;
            graphics.fill(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1,
                    hovered == index || focused ? theme().accentSoft() : theme().surface2());
            graphics.renderOutline(x, y, CELL_SIZE - 1, CELL_SIZE - 1, focused ? theme().text() : theme().border());
            renderMember(graphics, target, filtered.get(index), x + 3, y + 3);
        }
        if (filtered.isEmpty()) {
            graphics.drawWordWrap(font, tr(tag.members().isEmpty() ? "unavailable" : "contents.empty"),
                    grid.getX() + 4, grid.getY() + 8, grid.getWidth() - 8, theme().textMuted());
        }
        if (maxScroll() > 0) {
            int x = bounds.getX() + bounds.getWidth() - 8;
            int thumbHeight = thumbHeight();
            int y = grid.getY() + scroll * (grid.getHeight() - thumbHeight) / maxScroll();
            graphics.fill(x, grid.getY(), x + 4, grid.getY() + grid.getHeight(), theme().border());
            graphics.fill(x, y, x + 4, y + thumbHeight, theme().accent());
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = itemAt(mouseX, mouseY);
        if (index < 0 && focus == 1 && cursor >= scroll * columns && cursor < (scroll + visibleRows) * columns) {
            index = cursor;
            mouseX = grid.getX() + cursor % columns * CELL_SIZE + CELL_SIZE - 3;
            mouseY = grid.getY() + (cursor / columns - scroll) * CELL_SIZE + CELL_SIZE - 3;
        }
        if (index < 0 || index >= filtered.size()) return;
        FilterTagCatalog.Member member = filtered.get(index);
        if (target == FilterTargetType.ITEMS) {
            graphics.raw().setTooltipForNextFrame(font, new ItemStack(BuiltInRegistries.ITEM.getValue(member.id())), mouseX, mouseY);
        } else {
            graphics.renderTooltip(font, List.of(Component.literal(member.name()),
                    Component.literal(member.id().toString()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private int itemAt(double mouseX, double mouseY) {
        if (mouseX < grid.getX() || mouseY < grid.getY()
                || mouseX >= grid.getX() + grid.getWidth() || mouseY >= grid.getY() + grid.getHeight()) return -1;
        int column = ((int) mouseX - grid.getX()) / CELL_SIZE;
        int row = ((int) mouseY - grid.getY()) / CELL_SIZE;
        int index = (scroll + row) * columns + column;
        return index < filtered.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
    }

    private int thumbHeight() {
        return Math.max(8, grid.getHeight() * visibleRows / (maxScroll() + visibleRows));
    }

    private void dragScrollbar(double mouseY) {
        int thumbHeight = thumbHeight();
        double fraction = (mouseY - grid.getY() - thumbHeight / 2.0) / Math.max(1, grid.getHeight() - thumbHeight);
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        if (back.contains((int) mouseX, (int) mouseY)) { close.run(); return; }
        if (search.isMouseOver(mouseX, mouseY)) {
            setFocus(0);
            search.mouseClicked(ClientInput.mouse(mouseX, mouseY, button), false);
            return;
        }
        int scrollbarX = bounds.getX() + bounds.getWidth() - 10;
        if (maxScroll() > 0 && mouseX >= scrollbarX && mouseX < scrollbarX + 8
                && mouseY >= grid.getY() && mouseY < grid.getY() + grid.getHeight()) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return;
        }
        int index = itemAt(mouseX, mouseY);
        if (index >= 0) {
            cursor = index;
            setFocus(1);
        }
    }

    void mouseDragged(double mouseY) {
        if (draggingScrollbar) dragScrollbar(mouseY);
    }

    void mouseReleased() {
        draggingScrollbar = false;
    }

    void mouseScrolled(double mouseX, double mouseY, double amount) {
        if (bounds.contains((int) mouseX, (int) mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        }
    }

    void keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close.run(); return; }
        if (key == GLFW.GLFW_KEY_TAB) {
            setFocus(Math.floorMod(focus + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), 3));
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (focus == 2) close.run();
            else setFocus(1);
            return;
        }
        if (focus == 0) { search.keyPressed(ClientInput.key(key, scan, modifiers)); return; }
        if (focus != 1) return;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> moveCursor(cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> moveCursor(cursor + 1);
            case GLFW.GLFW_KEY_UP -> moveCursor(cursor - columns);
            case GLFW.GLFW_KEY_DOWN -> moveCursor(cursor + columns);
            case GLFW.GLFW_KEY_PAGE_UP -> moveCursor(cursor - columns * visibleRows);
            case GLFW.GLFW_KEY_PAGE_DOWN -> moveCursor(cursor + columns * visibleRows);
            case GLFW.GLFW_KEY_HOME -> moveCursor(0);
            case GLFW.GLFW_KEY_END -> moveCursor(filtered.size() - 1);
        }
    }

    void charTyped(char character, int modifiers) {
        if (focus == 0) search.charTyped(ClientInput.character(character));
    }

    private void moveCursor(int index) {
        if (filtered.isEmpty()) return;
        cursor = Mth.clamp(index, 0, filtered.size() - 1);
        int row = cursor / columns;
        scroll = Mth.clamp(scroll, Math.max(0, row - visibleRows + 1), row);
    }

    private void setFocus(int value) {
        focus = value;
        search.setFocused(value == 0);
    }
}
