package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.ClientInput;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.LegacyContainerScreen;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import me.almana.logisticsnetworks.network.OpenNodeFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Optional;

import static me.almana.logisticsnetworks.client.screen.RecipeCardCells.*;

public class RecipeCardScreen extends LegacyContainerScreen<RecipeCardMenu> {

    // Mutable shadow, parent fields final
    protected int imageWidth = WIDTH;
    protected int imageHeight = HEIGHT;

    private static final int COLOR_PANEL = 0xFF1F1F1F;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int CELLS = RecipeCardData.MAX_INGREDIENTS;
    private static final int MIN_CELLS = 5;
    private static final int OUTPUT_X = 8;
    private static final int OUTPUT_Y = 20;
    private static final int BLOCK_X = 108;
    private static final int BLOCK_Y = 21;
    private static final int BLOCK_W = 72;
    private static final int PATTERN_X = 88;
    private static final int PATTERN_Y = 20;
    private static final int WIDTH = 188;
    private static final int HEIGHT = RecipeCardMenu.INV_Y + 80;

    private final EditBox[] counts = new EditBox[CELLS];
    private final IngredientDetailHost host = new IngredientDetailHost(this);
    private final EntryDetailPage page = new EntryDetailPage(Minecraft.getInstance().font, host);
    private RecipeCardData known;
    private final boolean patterns = LinkedStorage.isBackendLoaded(StorageBackend.AE2)
            || LinkedStorage.isBackendLoaded(StorageBackend.REFINED_STORAGE);
    private final ItemStack patternIcon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(
            LinkedStorage.isBackendLoaded(StorageBackend.AE2) ? "ae2:blank_pattern" : "refinedstorage:pattern")));

    public RecipeCardScreen(RecipeCardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        titleLabelY = 10_000;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        for (int i = 0; i < CELLS; i++) {
            counts[i] = addRenderableWidget(countBox(font, leftPos + cellX(i), topPos + cellY(i)));
        }
        reconcile();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        reconcile();
        if (host.frozen() && page.isOpen()) page.close();
        page.tick();
    }

    private void reconcile() {
        // External change, drop stale focus
        if (menu.data() != known) {
            known = menu.data();
            setFocused(null);
            host.freeze();
        }
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        for (int i = 0; i < CELLS; i++) {
            counts[i].visible = i < list.size();
            if (counts[i].visible) fit(counts[i], list.get(i));
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

    void edited() {
        known = menu.data();
    }

    Rect2i pagePanel() {
        return new Rect2i(leftPos + 4, topPos + 20, imageWidth - 8, imageHeight - 24);
    }

    // Recenters like init does
    void resizePage(int minWidth, int minHeight) {
        int pageWidth = Math.max(WIDTH, minWidth);
        int pageHeight = Math.max(HEIGHT, minHeight);
        if (pageWidth == imageWidth && pageHeight == imageHeight) return;
        imageWidth = pageWidth;
        imageHeight = pageHeight;
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int action = ClientControls.resolveMouseAction(mouseX, mouseY, button);
        if (page.isOpen()) {
            if (action != -1 && !page.mouseClicked(mouseX, mouseY, action)) page.close();
            return true;
        }
        commitCount();
        boolean handled = action != -1 && clickCard((int) mouseX - leftPos, (int) mouseY - topPos, action);
        known = menu.data();
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return page.isOpen() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return page.isOpen() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page.isOpen()) return page.mouseScrolled(mouseX, mouseY, scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (page.isOpen()) return page.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (page.isOpen()) {
            if (key == 256) page.escape();
            else page.keyPressed(key, scan, modifiers);
            return true;
        }
        for (EditBox box : counts) {
            if (!box.isFocused()) continue;
            if (key == 256) {
                commitCount();
                return super.keyPressed(key, scan, modifiers);
            }
            if (key == 257 || key == 335) commitCount();
            else box.keyPressed(ClientInput.key(key, scan, modifiers));
            return true;
        }
        int action = ClientControls.resolveKeyAction(key, scan, modifiers);
        if (action != -1 && clickCard((int) ClientControls.cursorX(minecraft) - leftPos,
                (int) ClientControls.cursorY(minecraft) - topPos, action)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        if (page.isOpen()) page.close();
        commitCount();
        if (menu.isNodeTarget()) {
            ClientPacketDistributor.sendToServer(new OpenNodeFilterPayload(menu.entityId(), menu.channel(),
                    menu.filterSlot(), VirtualFilterType.EXISTING));
        } else if (menu.isEntryTarget()) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RecipeCardMenu.ID_BACK);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        if (page.isOpen()) page.close();
        commitCount();
        super.removed();
    }

    private boolean clickCard(int x, int y, int action) {
        ItemStack carried = menu.getCarried();
        if (inside(x, y, OUTPUT_X, OUTPUT_Y, 16, 16)) {
            ClickSound.play();
            if (action == 1) menu.edit(data -> data.withOutput(Optional.empty()));
            else if (!carried.isEmpty()) {
                menu.edit(data -> data.withOutput(Optional.of(StackSnapshot.of(carried.copyWithCount(1)))));
            }
            return true;
        }
        if (patterns && inside(x, y, PATTERN_X, PATTERN_Y, 16, 16)) {
            ClickSound.play();
            if (action == 0 && !carried.isEmpty()) fillFromPattern(carried);
            return true;
        }
        if (inside(x, y, BLOCK_X, BLOCK_Y, BLOCK_W, 14)) {
            ClickSound.play();
            menu.edit(data -> data.withBlocking(!data.blocking()));
            return true;
        }
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        int cell = slotAt(x, y, droppableCells());
        if (cell >= 0) {
            ClickSound.play();
            if (action == 1 && cell < list.size()) menu.edit(data -> data.withoutIngredient(cell));
            else if (action == 0 && !carried.isEmpty() && ClientControls.modifier1Down()) {
                dropResource(cell, contents(carried));
            } else if (action == 0 && !carried.isEmpty()) dropIngredient(cell, carried);
            return true;
        }
        int pencil = editButtonAt(x, y, list.size());
        if (pencil >= 0) {
            ClickSound.play();
            host.track();
            page.open(pencil);
            return true;
        }
        int tag = itemButtonAt(x, y, list, TAG_X);
        if (tag >= 0) {
            ClickSound.play();
            menu.edit(data -> data.withIngredient(tag, data.ingredients().get(tag).nextTag()));
            return true;
        }
        return false;
    }

    // None under the open page
    public int droppableCells() {
        return page.isOpen() ? 0 : Math.min(CELLS, Math.max(MIN_CELLS, menu.data().ingredients().size() + 1));
    }

    public Rect2i cellArea(int index) {
        return new Rect2i(leftPos + cellX(index), topPos + cellY(index) + 1, 16, 16);
    }

    private void dropIngredient(int index, ItemStack stack) {
        dropResource(index, RecipeCardData.Ingredient.ofItem(stack, stack.getCount()));
    }

    public void dropResource(int index, RecipeCardData.Ingredient ingredient) {
        commitCount();
        int at = Math.min(index, menu.data().ingredients().size());
        menu.edit(data -> data.withIngredient(at, ingredient));
    }

    private void fillFromPattern(ItemStack pattern) {
        List<RecipeCardData.Ingredient> ingredients = LinkedStorage.readPatternIngredients(pattern, minecraft.level);
        if (ingredients.isEmpty()) return;
        Optional<StackSnapshot> output = LinkedStorage.readPatternOutputs(pattern, minecraft.level).stream()
                .findFirst()
                .map(entry -> StackSnapshot.of(entry.item().copyWithCount(entry.amount())));
        menu.edit(data -> new RecipeCardData(ingredients, output, data.blocking()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (page.isOpen()) {
            // Cover the card's slots
            graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, COLOR_PANEL);
            page.render(graphics, mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty()) renderCardTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!page.isOpen()) super.extractTooltip(graphics, mouseX, mouseY);
    }

    private void renderCardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        List<RecipeCardData.Ingredient> list = menu.data().ingredients();
        int cell = slotAt(x, y, droppableCells());
        if (cell >= 0) {
            graphics.renderTooltip(font, RecipeCardCells.tooltip(cell < list.size() ? list.get(cell) : null),
                    mouseX, mouseY);
            return;
        }
        int pencil = editButtonAt(x, y, list.size());
        if (pencil >= 0) {
            graphics.renderTooltip(font, editTooltip(list.get(pencil)), mouseX, mouseY);
            return;
        }
        int tag = itemButtonAt(x, y, list, TAG_X);
        if (tag >= 0) {
            graphics.renderTooltip(font, Component.translatable("gui.logisticsnetworks.recipe_card.tag_toggle"),
                    mouseX, mouseY);
        } else if (inside(x, y, BLOCK_X, BLOCK_Y, BLOCK_W, 14)) {
            graphics.raw().setTooltipForNextFrame(font, font.split(
                    Component.translatable("gui.logisticsnetworks.recipe_card.blocking.tooltip"), 180), mouseX, mouseY);
        } else if (inside(x, y, OUTPUT_X, OUTPUT_Y, 16, 16)) {
            menu.data().output().ifPresent(output -> graphics.renderTooltip(font, output.toStack(), mouseX, mouseY));
        } else if (patterns && inside(x, y, PATTERN_X, PATTERN_Y, 16, 16)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("gui.logisticsnetworks.recipe_card.pattern"),
                    Component.translatable("gui.logisticsnetworks.recipe_card.pattern.tooltip")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
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
        if (patterns) {
            slot(graphics, lp + PATTERN_X, tp + PATTERN_Y);
            graphics.renderItem(patternIcon, lp + PATTERN_X, tp + PATTERN_Y);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(lp + PATTERN_X, tp + PATTERN_Y, lp + PATTERN_X + 16, tp + PATTERN_Y + 16, 0xAA2A2A2A);
            graphics.pose().popPose();
        }
        graphics.fill(lp + BLOCK_X, tp + BLOCK_Y, lp + BLOCK_X + BLOCK_W, tp + BLOCK_Y + 14, COLOR_SLOT_BORDER);
        graphics.fill(lp + BLOCK_X + 1, tp + BLOCK_Y + 1, lp + BLOCK_X + BLOCK_W - 1, tp + BLOCK_Y + 13,
                data.blocking() ? COLOR_BTN_ON : COLOR_BTN);
        graphics.drawCenteredString(font, Component.translatable(data.blocking()
                        ? "tooltip.logisticsnetworks.recipe_card.blocking_on"
                        : "tooltip.logisticsnetworks.recipe_card.blocking_off"),
                lp + BLOCK_X + BLOCK_W / 2, tp + BLOCK_Y + 3, COLOR_TEXT);
        for (int i = 0; i < droppableCells(); i++) {
            RecipeCardCells.cell(graphics, font, lp + cellX(i), tp + cellY(i) + 1,
                    i < data.ingredients().size() ? data.ingredients().get(i) : null);
        }
        graphics.drawString(font, playerInventoryTitle, lp + RecipeCardMenu.INV_X, tp + RecipeCardMenu.INV_Y - 11,
                COLOR_MUTED, false);
        for (Slot slot : menu.slots) {
            slot(graphics, lp + slot.x, tp + slot.y);
        }
    }
}
