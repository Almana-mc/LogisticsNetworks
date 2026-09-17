package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.GraphMenuContext;
import me.almana.logisticsnetworks.network.ReturnToComputerPayload;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class NodeScreen extends NodeEditorScreen<NodeMenu> {

    public NodeScreen(NodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void rebuildPageLayout() {
        super.rebuildPageLayout();
        if (returnsToTable()) {
            addRenderableWidget(new BackToTableButton(leftPos, topPos - 15));
        }
    }

    private boolean returnsToTable() {
        GraphMenuContext context = menu.getReturnContext();
        return context != null && context.origin() == GraphMenuContext.Origin.TABLE;
    }

    private void returnToTable() {
        if (!returnsToTable() || !menu.getCarried().isEmpty()) return;
        commitPendingEdits();
        ClientPacketDistributor.sendToServer(new ReturnToComputerPayload(menu.getReturnContext().networkId()));
    }

    @Override
    public void onClose() {
        if (returnsToTable()) returnToTable();
        else super.onClose();
    }

    private final class BackToTableButton extends Button {

        private BackToTableButton(int x, int y) {
            super(x, y, 88, 16, Component.translatable("gui.logisticsnetworks.node.back_to_table"),
                    button -> NodeScreen.this.returnToTable(), DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor raw, int mouseX, int mouseY, float partialTick) {
            GuiGraphics graphics = new GuiGraphics(raw);
            var theme = NodeScreen.this.theme();
            int border = isHoveredOrFocused() ? theme.accent() : theme.border();
            int bottom = getY() + getHeight();
            ThemePaint.roundRect(graphics, getX(), getY(), getWidth(), getHeight(), 2,
                    theme.surface(), theme.sharpCorners());
            ThemePaint.roundOutline(graphics, getX(), getY(), getWidth(), getHeight(), 2,
                    border, theme.sharpCorners());
            graphics.fill(getX() + 1, bottom - 2, getX() + getWidth() - 1, bottom, theme.surface());
            graphics.fill(getX(), bottom - 2, getX() + 1, bottom, border);
            graphics.fill(getX() + getWidth() - 1, bottom - 2, getX() + getWidth(), bottom, border);
            ThemePaint.drawCentered(graphics, NodeScreen.this.font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 7) / 2, isHoveredOrFocused() ? theme.text() : theme.textMuted());
        }
    }
}
