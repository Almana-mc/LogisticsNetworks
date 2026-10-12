package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class RecipeCardScreen extends AbstractContainerScreen<RecipeCardMenu> {

    private static final int COLOR_PANEL = 0xFF1F1F1F;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_SLOT = 0xFF2A2A2A;
    private static final int COLOR_SLOT_BORDER = 0xFF444444;
    private static final int COLOR_BTN = 0xFF2E2E2E;
    private static final int COLOR_BTN_ON = 0xFF2E5E2E;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF999999;
    private static final int COLOR_TAG = 0xFFFFD24A;

    private static final int CELLS = RecipeCardData.MAX_INGREDIENTS;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 40;
    private static final int CELL_W = 84;
    private static final int CELL_H = 20;
    private static final int OUTPUT_X = 8;
    private static final int OUTPUT_Y = 20;
    private static final int BLOCK_X = 96;
    private static final int BLOCK_Y = 21;
    private static final int BLOCK_W = 72;

    private final EditBox[] counts = new EditBox[CELLS];

    public RecipeCardScreen(RecipeCardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = RecipeCardMenu.INV_Y + 80;
        titleLabelY = 10_000;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < CELLS; i++) {
            EditBox box = new EditBox(font, leftPos + cellX(i) + 21, topPos + cellY(i) + 5, 26, 10,
                    Component.empty());
            box.setMaxLength(4);
            box.setBordered(false);
            box.setTextColor(COLOR_TEXT);
            box.setFilter(value -> value.isEmpty() || value.matches("\\d+"));
            counts[i] = addRenderableWidget(box);
        }
        reconcile();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        reconcile();
    }

    private void reconcile() {
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        for (int i = 0; i < CELLS; i++) {
            counts[i].visible = i < list.size();
            String count = counts[i].visible ? Integer.toString(list.get(i).count()) : "";
            if (!counts[i].isFocused() && !counts[i].getValue().equals(count)) counts[i].setValue(count);
        }
    }

    private void commitCount() {
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        for (int i = 0; i < CELLS; i++) {
            String value = counts[i].getValue();
            if (!counts[i].isFocused() || i >= list.size() || value.isEmpty()) continue;
            int index = i;
            int count = Integer.parseInt(value);
            if (count >= 1) menu.edit(data -> data.withIngredient(index, data.ingredients().get(index).withCount(count)));
        }
        setFocused(null);
        reconcile();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        commitCount();
        int action = ClientControls.resolveMouseAction(button);
        if (action != -1 && clickCard((int) mouseX - leftPos, (int) mouseY - topPos, action)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        for (EditBox box : counts) {
            if (!box.isFocused()) continue;
            if (key == 256) {
                commitCount();
                return super.keyPressed(key, scan, modifiers);
            }
            if (key == 257 || key == 335) commitCount();
            else box.keyPressed(key, scan, modifiers);
            return true;
        }
        int action = ClientControls.resolveKeyAction(key, scan);
        if (action != -1 && clickCard((int) ClientControls.cursorX(minecraft) - leftPos,
                (int) ClientControls.cursorY(minecraft) - topPos, action)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        commitCount();
        super.onClose();
    }

    private boolean clickCard(int x, int y, int action) {
        ItemStack carried = menu.getCarried();
        if (inside(x, y, OUTPUT_X, OUTPUT_Y, 16, 16)) {
            if (action == 1) menu.edit(data -> data.withOutput(Optional.empty()));
            else if (!carried.isEmpty()) {
                menu.edit(data -> data.withOutput(Optional.of(StackSnapshot.of(carried.copyWithCount(1)))));
            }
            return true;
        }
        if (inside(x, y, BLOCK_X, BLOCK_Y, BLOCK_W, 14)) {
            menu.edit(data -> data.withBlocking(!data.blocking()));
            return true;
        }
        int size = menu.data().ingredients().size();
        for (int i = 0; i < droppableCells(); i++) {
            int index = i;
            if (inside(x, y, cellX(i), cellY(i) + 1, 16, 16)) {
                if (action == 1 && index < size) menu.edit(data -> data.withoutIngredient(index));
                else if (action == 0 && !carried.isEmpty()) dropIngredient(index, carried);
                return true;
            }
            if (index < size && inside(x, y, cellX(i) + 52, cellY(i) + 2, 14, 14)) {
                menu.edit(data -> data.withIngredient(index, data.ingredients().get(index).nextTag()));
                return true;
            }
        }
        return false;
    }

    public int droppableCells() {
        return Math.min(CELLS, menu.data().ingredients().size() + 1);
    }

    public Rect2i cellArea(int index) {
        return new Rect2i(leftPos + cellX(index), topPos + cellY(index) + 1, 16, 16);
    }

    public void dropIngredient(int index, ItemStack stack) {
        int at = Math.min(index, menu.data().ingredients().size());
        menu.edit(data -> data.withIngredient(at, RecipeCardData.Ingredient.ofItem(stack, stack.getCount())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty()) renderCardTooltip(graphics, mouseX, mouseY);
    }

    private void renderCardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        for (int i = 0; i < list.size(); i++) {
            if (inside(x, y, cellX(i), cellY(i) + 1, 16, 16)) {
                graphics.renderComponentTooltip(font, List.of(list.get(i).label(),
                        Component.translatable("gui.logisticsnetworks.recipe_card.remove_hint")
                                .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
            if (inside(x, y, cellX(i) + 52, cellY(i) + 2, 14, 14)) {
                graphics.renderTooltip(font, Component.translatable("gui.logisticsnetworks.recipe_card.tag_toggle"),
                        mouseX, mouseY);
                return;
            }
        }
        if (inside(x, y, BLOCK_X, BLOCK_Y, BLOCK_W, 14)) {
            graphics.renderTooltip(font, font.split(
                    Component.translatable("gui.logisticsnetworks.recipe_card.blocking.tooltip"), 180), mouseX, mouseY);
        } else if (inside(x, y, OUTPUT_X, OUTPUT_Y, 16, 16)) {
            menu.data().output().ifPresent(output -> graphics.renderTooltip(font, output.toStack(), mouseX, mouseY));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int lp = leftPos;
        int tp = topPos;
        RecipeCardData data = menu.data();
        graphics.fill(lp, tp, lp + imageWidth, tp + imageHeight, COLOR_BORDER);
        graphics.fill(lp + 1, tp + 1, lp + imageWidth - 1, tp + imageHeight - 1, COLOR_PANEL);
        graphics.drawCenteredString(font, title, lp + imageWidth / 2, tp + 6, COLOR_TEXT);

        slot(graphics, lp + OUTPUT_X, tp + OUTPUT_Y);
        data.output().ifPresent(output -> graphics.renderItem(output.toStack(), lp + OUTPUT_X, tp + OUTPUT_Y));
        graphics.drawString(font, Component.translatable("gui.logisticsnetworks.recipe_card.output"),
                lp + OUTPUT_X + 20, tp + OUTPUT_Y + 4, COLOR_MUTED, false);

        graphics.fill(lp + BLOCK_X, tp + BLOCK_Y, lp + BLOCK_X + BLOCK_W, tp + BLOCK_Y + 14, COLOR_SLOT_BORDER);
        graphics.fill(lp + BLOCK_X + 1, tp + BLOCK_Y + 1, lp + BLOCK_X + BLOCK_W - 1, tp + BLOCK_Y + 13,
                data.blocking() ? COLOR_BTN_ON : COLOR_BTN);
        graphics.drawCenteredString(font, Component.translatable(data.blocking()
                        ? "tooltip.logisticsnetworks.recipe_card.blocking_on"
                        : "tooltip.logisticsnetworks.recipe_card.blocking_off"),
                lp + BLOCK_X + BLOCK_W / 2, tp + BLOCK_Y + 3, COLOR_TEXT);

        for (int i = 0; i < droppableCells(); i++) {
            renderCell(graphics, i, i < data.ingredients().size() ? data.ingredients().get(i) : null);
        }

        graphics.drawString(font, playerInventoryTitle, lp + 8, tp + RecipeCardMenu.INV_Y - 11, COLOR_MUTED, false);
        for (Slot slot : menu.slots) {
            slot(graphics, lp + slot.x, tp + slot.y);
        }
    }

    private void renderCell(GuiGraphics graphics, int index, @Nullable RecipeCardData.Ingredient ingredient) {
        int x = leftPos + cellX(index);
        int y = topPos + cellY(index) + 1;
        slot(graphics, x, y);
        if (ingredient == null) return;
        graphics.renderItem(ingredient.icon(), x, y);
        if (ingredient.tag().isPresent()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, "#", x + 11, y + 8, COLOR_TAG, true);
            graphics.pose().popPose();
        }
        graphics.fill(x + 18, y + 2, x + 48, y + 14, COLOR_SLOT_BORDER);
        graphics.fill(x + 19, y + 3, x + 47, y + 13, COLOR_SLOT);
        graphics.fill(x + 52, y + 1, x + 66, y + 15, COLOR_SLOT_BORDER);
        graphics.fill(x + 53, y + 2, x + 65, y + 14, COLOR_BTN);
        graphics.drawCenteredString(font, "#", x + 59, y + 4, COLOR_TEXT);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    private static int cellX(int index) {
        return GRID_X + (index % 2) * CELL_W;
    }

    private static int cellY(int index) {
        return GRID_Y + (index / 2) * CELL_H;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
