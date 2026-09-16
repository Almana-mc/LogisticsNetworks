package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class FilterTagPicker {
    private static final int ROW_HEIGHT = 26;
    private static final int ACCENT = 0xFF44BB44;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFBBBBBB;
    private final Font font;
    private final FilterTargetType target;
    private final List<FilterTagCatalog.Entry> entries;
    private final EditBox search;
    private final Consumer<String> apply;
    private final Runnable close;
    private List<FilterTagCatalog.Entry> filtered;
    private FilterTagCatalog.Entry selected;
    private Rect2i bounds;
    private Rect2i rows;
    private Rect2i back;
    private Rect2i use;
    private Rect2i tagHeader;
    private Rect2i countHeader;
    private int visibleRows;
    private int scroll;
    private int focus;
    private boolean draggingScrollbar;
    private boolean sortByCount = true;
    private boolean descending = true;
    private FilterTagContents contents;

    FilterTagPicker(Font font, FilterTargetType target, ResourceLocation current, String selectedTag,
                    Consumer<String> apply, Runnable close, int width, int height) {
        this.font = font;
        this.target = target;
        this.apply = apply;
        this.close = close;
        entries = FilterTagCatalog.load(target, current, selectedTag);
        filtered = FilterTagCatalog.search(entries, "", sortByCount, descending);
        selected = entries.stream().filter(entry -> entry.id().toString().equals(selectedTag)).findFirst().orElse(null);
        search = new EditBox(font, 0, 0, 100, 16, tr("search"));
        search.setMaxLength(256);
        search.setHint(tr("search_hint." + target.getSerializedName()));
        search.setResponder(query -> refresh());
        search.setFocused(true);
        layout(width, height);
        if (selected != null) scroll = Mth.clamp(filtered.indexOf(selected), 0, maxScroll());
    }

    boolean isSearchFocused() {
        return contents == null ? search.isFocused() : contents.isSearchFocused();
    }

    private void layout(int width, int height) {
        int panelWidth = Math.min(380, width - 12);
        int panelHeight = Math.min(256, height - 28);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        bounds = new Rect2i(x, y, panelWidth, panelHeight);
        back = new Rect2i(x + 6, y + 6, 50, 14);
        use = new Rect2i(x + panelWidth - 72, y + panelHeight - 20, 66, 14);
        tagHeader = new Rect2i(x + 15, y + 55, examplesX() - x - 20, 14);
        int countWidth = font.width(tr("column." + target.getSerializedName())) + 14;
        countHeader = new Rect2i(x + panelWidth - 14 - countWidth, y + 55, countWidth, 14);
        search.setX(x + 46);
        search.setY(y + 37);
        search.setWidth(panelWidth - 52);
        visibleRows = Math.max(1, (panelHeight - 130) / ROW_HEIGHT);
        rows = new Rect2i(x + 4, y + 72, panelWidth - 14, visibleRows * ROW_HEIGHT);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void refresh() {
        filtered = FilterTagCatalog.search(entries, search.getValue(), sortByCount, descending);
        scroll = 0;
        draggingScrollbar = false;
        if (!filtered.contains(selected)) selected = null;
    }

    private void sortBy(boolean byCount) {
        descending = sortByCount == byCount ? !descending : byCount;
        sortByCount = byCount;
        setFocus(byCount ? 2 : 1);
        refresh();
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float delta, int width, int height) {
        layout(width, height);
        if (contents != null) {
            contents.render(graphics, mouseX, mouseY, delta, bounds);
            return;
        }
        int x = bounds.getX();
        int y = bounds.getY();
        int w = bounds.getWidth();
        graphics.fill(x, y, x + w, y + bounds.getHeight(), 0xFF101010);
        graphics.renderOutline(x, y, w, bounds.getHeight(), ACCENT);
        drawButton(graphics, font, back, tr("back"), true, focus == 5, mouseX, mouseY);
        graphics.drawString(font, tr("title"), x + 62, y + 9, TEXT, false);
        graphics.drawString(font, tr("help"), x + 6, y + 25, MUTED, false);
        graphics.drawString(font, tr("search"), x + 6, y + 41, TEXT, false);
        search.render(graphics, mouseX, mouseY, delta);
        renderSortHeader(graphics, tagHeader, tr("tag"), false, mouseX, mouseY);
        graphics.drawString(font, tr("examples"), examplesX(), y + 59, MUTED, false);
        renderSortHeader(graphics, countHeader, tr("column." + target.getSerializedName()), true, mouseX, mouseY);
        renderRows(graphics, mouseX, mouseY);
        renderPreview(graphics);
        drawButton(graphics, font, use, tr("use"), canApply(), focus == 4, mouseX, mouseY);
        graphics.drawString(font, tr("one_per_slot"), x + 6, use.getY() + 3, MUTED, false);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderSortHeader(GuiGraphics graphics, Rect2i area, Component label, boolean byCount,
                                  int mouseX, int mouseY) {
        boolean hovered = area.contains(mouseX, mouseY);
        boolean focused = focus == (byCount ? 2 : 1);
        if (hovered || focused) {
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0xFF292929);
        }
        if (focused) graphics.renderOutline(area.getX(), area.getY(), area.getWidth(), area.getHeight(), TEXT);
        int color = sortByCount == byCount ? ACCENT : hovered || focused ? TEXT : MUTED;
        graphics.drawString(font, label, area.getX() + 3, area.getY() + 4, color, false);
        if (sortByCount != byCount) return;
        int arrowX = area.getX() + font.width(label) + 9;
        for (int row = 0; row < 3; row++) {
            int halfWidth = descending ? 2 - row : row;
            graphics.fill(arrowX - halfWidth, area.getY() + 6 + row,
                    arrowX + halfWidth + 1, area.getY() + 7 + row, color);
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = scroll; index < Math.min(filtered.size(), scroll + visibleRows); index++) {
            FilterTagCatalog.Entry entry = filtered.get(index);
            int y = rows.getY() + (index - scroll) * ROW_HEIGHT;
            boolean chosen = entry.equals(selected);
            if (chosen || rowAt(mouseX, mouseY) == index) {
                graphics.fill(rows.getX(), y, rows.getX() + rows.getWidth(), y + ROW_HEIGHT,
                        chosen ? 0xFF2A4A2A : 0xFF292929);
            }
            graphics.renderOutline(rows.getX() + 3, y + 8, 8, 8, chosen ? ACCENT : MUTED);
            if (chosen) graphics.fill(rows.getX() + 5, y + 10, rows.getX() + 9, y + 14, ACCENT);
            if (chosen && focus == 3) graphics.renderOutline(rows.getX(), y, rows.getWidth(), ROW_HEIGHT, TEXT);
            int textX = rows.getX() + 15;
            int textWidth = examplesX() - textX - 6;
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), textWidth), textX, y + 4,
                    entry.related() ? ACCENT : TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth("#" + entry.id(), textWidth), textX, y + 15, MUTED, false);
            for (int icon = 0; icon < Math.min(3, entry.members().size()); icon++) {
                renderMember(graphics, target, entry.members().get(icon), examplesX() + icon * 18, y + 5);
            }
            String count = Integer.toString(entry.members().size());
            graphics.drawString(font, count, bounds.getX() + bounds.getWidth() - 14 - font.width(count), y + 9, TEXT, false);
        }
        if (filtered.isEmpty()) graphics.drawWordWrap(font, tr("empty"), rows.getX() + 4,
                rows.getY() + 10, rows.getWidth() - 8, MUTED);
        if (focus == 3 && selected == null) {
            graphics.renderOutline(rows.getX(), rows.getY(), rows.getWidth(), rows.getHeight(), TEXT);
        }
        renderScrollbar(graphics);
    }

    static void renderMember(GuiGraphics graphics, FilterTargetType target, FilterTagCatalog.Member member, int x, int y) {
        switch (target) {
            case ITEMS -> graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(member.id())), x, y);
            case FLUIDS -> FilterResourceRenderer.renderFluid(graphics,
                    new FluidStack(BuiltInRegistries.FLUID.get(member.id()), 1000), x, y);
            case CHEMICALS -> FilterResourceRenderer.renderChemical(graphics, member.id().toString(), x, y);
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        int x = bounds.getX() + 6;
        int y = bounds.getY() + bounds.getHeight() - 54;
        int width = bounds.getWidth() - 12;
        graphics.fill(x, y - 4, x + width, y - 3, 0xFF444444);
        Component title = selected == null ? tr("select") : Component.literal(selected.name() + " - ")
                .append(tr("count." + target.getSerializedName(), selected.members().size()));
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width), x, y, TEXT, false);
        String preview = selected == null ? "" : selected.members().isEmpty() ? tr("unavailable").getString()
                : selected.members().stream().limit(4).map(FilterTagCatalog.Member::name).collect(Collectors.joining(", "));
        if (selected != null && selected.members().size() > 4) preview += tr("more", selected.members().size() - 4).getString();
        graphics.drawString(font, font.plainSubstrByWidth(preview, width), x, y + 12, MUTED, false);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (tagHeader.contains(mouseX, mouseY) || countHeader.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, tr(tagHeader.contains(mouseX, mouseY) ? "sort.name" : "sort.count"), mouseX, mouseY);
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return;
        FilterTagCatalog.Entry entry = filtered.get(index);
        int icon = (mouseX - examplesX()) / 18;
        List<Component> lines = new ArrayList<>();
        if (mouseX >= examplesX() && icon < Math.min(3, entry.members().size())) {
            lines.add(Component.literal(entry.members().get(icon).name()));
        } else {
            lines.add(Component.literal(entry.name()));
            lines.add(Component.literal("#" + entry.id()).withStyle(ChatFormatting.GRAY));
            if (entry.related()) lines.add(tr("related").withStyle(ChatFormatting.GREEN));
            if (entry.members().isEmpty()) lines.add(tr("unavailable").withStyle(ChatFormatting.GRAY));
        }
        lines.add(tr("view_all." + target.getSerializedName()).withStyle(ChatFormatting.YELLOW));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    static void drawButton(GuiGraphics graphics, Font font, Rect2i area, Component label, boolean active,
                           boolean focused, int mouseX, int mouseY) {
        boolean hovered = active && area.contains(mouseX, mouseY);
        graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(),
                hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.renderOutline(area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                focused || hovered ? TEXT : 0xFF4A4A4A);
        graphics.drawCenteredString(font, label, area.getX() + area.getWidth() / 2, area.getY() + 3,
                active ? TEXT : 0xFF777777);
    }

    private int examplesX() {
        return bounds.getX() + bounds.getWidth() - 76 - Math.max(32,
                font.width(tr("column." + target.getSerializedName())));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!rows.contains((int) mouseX, (int) mouseY)) return -1;
        int index = scroll + ((int) mouseY - rows.getY()) / ROW_HEIGHT;
        return index < filtered.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - visibleRows);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() == 0) return;
        int x = rows.getX() + rows.getWidth() + 2;
        int thumbHeight = Math.max(8, rows.getHeight() * visibleRows / filtered.size());
        int y = rows.getY() + scroll * (rows.getHeight() - thumbHeight) / maxScroll();
        graphics.fill(x, rows.getY(), x + 4, rows.getY() + rows.getHeight(), 0xFF333333);
        graphics.fill(x, y, x + 4, y + thumbHeight, ACCENT);
    }

    private void dragScrollbar(double mouseY) {
        int thumbHeight = Math.max(8, rows.getHeight() * visibleRows / filtered.size());
        double fraction = (mouseY - rows.getY() - thumbHeight / 2.0) / Math.max(1, rows.getHeight() - thumbHeight);
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    void mouseClicked(double mouseX, double mouseY, int button, boolean leftControlDown) {
        if (contents != null) {
            contents.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (button != 0) return;
        if (back.contains((int) mouseX, (int) mouseY)) { close.run(); return; }
        if (use.contains((int) mouseX, (int) mouseY)) { apply(); return; }
        if (tagHeader.contains((int) mouseX, (int) mouseY)) { sortBy(false); return; }
        if (countHeader.contains((int) mouseX, (int) mouseY)) { sortBy(true); return; }
        if (search.isMouseOver(mouseX, mouseY)) {
            setFocus(0);
            search.mouseClicked(mouseX, mouseY, button);
            return;
        }
        int scrollbarX = rows.getX() + rows.getWidth();
        if (maxScroll() > 0 && mouseX >= scrollbarX && mouseX < scrollbarX + 8
                && mouseY >= rows.getY() && mouseY < rows.getY() + rows.getHeight()) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            if (leftControlDown) { openContents(filtered.get(index)); return; }
            selected = filtered.get(index);
            setFocus(3);
        }
    }

    private void openContents(FilterTagCatalog.Entry entry) {
        draggingScrollbar = false;
        contents = new FilterTagContents(font, target, entry, bounds, () -> contents = null);
    }

    void mouseDragged(double mouseY) {
        if (contents != null) { contents.mouseDragged(mouseY); return; }
        if (draggingScrollbar) dragScrollbar(mouseY);
    }

    void mouseReleased() {
        if (contents != null) { contents.mouseReleased(); return; }
        draggingScrollbar = false;
    }

    void mouseScrolled(double mouseX, double mouseY, double amount) {
        if (contents != null) { contents.mouseScrolled(mouseX, mouseY, amount); return; }
        if (bounds.contains((int) mouseX, (int) mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        }
    }

    void keyPressed(int key, int scan, int modifiers) {
        if (contents != null) { contents.keyPressed(key, scan, modifiers); return; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { close.run(); return; }
        if (key == GLFW.GLFW_KEY_TAB) { setFocus(Math.floorMod(focus + (Screen.hasShiftDown() ? -1 : 1), 6)); return; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1);
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE && focus != 0) {
            if (focus == 3 && selected != null && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) openContents(selected);
            else if (focus == 1 || focus == 2) sortBy(focus == 2);
            else if (focus == 5) close.run();
            else apply();
            return;
        }
        if (focus == 0) search.keyPressed(key, scan, modifiers);
    }

    void charTyped(char character, int modifiers) {
        if (contents != null) { contents.charTyped(character, modifiers); return; }
        if (focus == 0) search.charTyped(character, modifiers);
    }

    private void moveSelection(int direction) {
        if (filtered.isEmpty()) return;
        int index = filtered.indexOf(selected);
        index = index < 0 ? 0 : Mth.clamp(index + direction, 0, filtered.size() - 1);
        selected = filtered.get(index);
        scroll = Mth.clamp(scroll, Math.max(0, index - visibleRows + 1), index);
        setFocus(3);
    }

    private void setFocus(int value) {
        focus = value;
        search.setFocused(value == 0);
    }

    private boolean canApply() {
        return selected != null && !selected.members().isEmpty();
    }

    private void apply() {
        if (canApply()) apply.accept(selected.id().toString());
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.logisticsnetworks.filter.tag_picker." + key, args);
    }
}
