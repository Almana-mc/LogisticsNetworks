package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.graph.GraphCanvas;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import me.almana.logisticsnetworks.menu.NodeGraphMenu;
import me.almana.logisticsnetworks.network.ConfirmGraphLabelsPayload;
import me.almana.logisticsnetworks.network.GraphLabelPreviewPayload;
import me.almana.logisticsnetworks.network.MoveGraphVerticesPayload;
import me.almana.logisticsnetworks.network.PreviewGraphLabelsPayload;
import me.almana.logisticsnetworks.network.RequestNetworkGraphPayload;
import me.almana.logisticsnetworks.network.RequestOpenGraphPayload;
import me.almana.logisticsnetworks.network.ResetGraphLayoutPayload;
import me.almana.logisticsnetworks.network.ReturnToComputerPayload;
import me.almana.logisticsnetworks.network.SetNodeLabelsPayload;
import me.almana.logisticsnetworks.network.SyncNetworkGraphPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class NodeGraphScreen extends NodeEditorScreen<NodeGraphMenu> {
    private static final int PANEL_CLOSED_LEFT = -264;
    private static final int PANEL_OPEN_LEFT = 8;
    private static final int PANEL_CANVAS_GAP = 272;
    private static final long PANEL_ANIMATION_MILLIS = 450;
    private static final ResourceLocation PREVIOUS_ICON = ResourceLocation.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/graph_left.png");
    private static final ResourceLocation NEXT_ICON = ResourceLocation.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/graph_right.png");

    private final NodeGraphSession session;
    private final NodeGraphMenu graphMenu;
    private double uiScale = 1;
    private int refreshTicks;
    private boolean openingSelection;
    private long selectionRequestedAt;
    private long panelAnimationStartedAt = -1;
    private Button previousButton;
    private Button nextButton;
    private LabelConfirmation labelConfirmation;
    private boolean labelRequestPending;
    private UUID labelRequestId;

    public NodeGraphScreen(NodeGraphMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        graphMenu = menu;
        session = NodeGraphSession.attach(this, menu.getGraphNetworkId());
    }

    @Override
    protected void init() {
        boolean startPanelAnimation = menu.getNode() != null && !session.editorOpen
                && panelAnimationStartedAt < 0;
        uiScale = Math.min(1.0, Math.min(width / 640.0, height / 360.0));
        width = (int) Math.ceil(width / uiScale);
        height = (int) Math.ceil(height / uiScale);
        super.init();
        if (startPanelAnimation) panelAnimationStartedAt = Util.getMillis();
        updatePanelLayout(Util.getMillis());
        updateSelection();
        session.editorOpen = menu.getNode() != null;
        PacketDistributor.sendToServer(new RequestNetworkGraphPayload(graphMenu.getGraphNetworkId()));
    }

    @Override
    protected void positionEditor() {
        leftPos = 8;
        topPos = height - 306;
    }

    @Override
    protected void rebuildPageLayout() {
        previousButton = null;
        nextButton = null;
        if (menu.getNode() != null) super.rebuildPageLayout();
        else clearWidgets();
        addButton(width - 224, 8, 48, "back", this::returnToComputer);
        addButton(width - 170, 8, 46, "fit", () -> canvas().fit());
        addButton(width - 118, 8, 110, "reset", this::resetLayout);
        if (menu.getNode() != null) {
            previousButton = addIconButton(leftPos + 6, topPos - 16, "previous", PREVIOUS_ICON,
                    () -> selectMember(-1));
            nextButton = addIconButton(leftPos + 232, topPos - 16, "next", NEXT_ICON,
                    () -> selectMember(1));
            updateMemberButtons();
        }
    }

    private GraphButton addButton(int x, int y, int w, String key, Runnable action) {
        return addRenderableWidget(new GraphButton(x, y, w, text(key), action, this::theme));
    }

    private Button addIconButton(int x, int y, String key, ResourceLocation icon, Runnable action) {
        return addRenderableWidget(new GraphIconButton(x, y, text(key), action, this::theme, icon));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new RequestNetworkGraphPayload(graphMenu.getGraphNetworkId()));
        }
        if (openingSelection && System.currentTimeMillis() - selectionRequestedAt > 3000) {
            openingSelection = false;
        }
    }

    public void receiveGraph(SyncNetworkGraphPayload snapshot) {
        if (!snapshot.networkId().equals(graphMenu.getGraphNetworkId())) return;
        session.snapshot = snapshot;
        canvas().update(snapshot.nodes(), snapshot.positions());
        updateSelection();
        GraphNode selected = selectedNode();
        String selectedKey = selected == null ? null : NetworkGraph.key(selected);
        boolean missingEditorNode = menu.getNode() != null && selected == null;
        if (!openingSelection && (missingEditorNode || !Objects.equals(selectedKey, canvas().getSelected()))) {
            openSelection(canvas().getPrimaryNode());
        }
    }

    private void updateSelection() {
        GraphNode selected = selectedNode();
        canvas().setSelected(selected == null ? null : NetworkGraph.key(selected),
                selected == null ? null : selected.nodeId());
        if (selected != null) menu.getNode().setNodeLabel(selected.label());
        updateMemberButtons();
    }

    private void updateMemberButtons() {
        boolean visible = selectedMembers().size() > 1;
        if (previousButton != null) {
            previousButton.visible = visible;
            previousButton.active = visible;
        }
        if (nextButton != null) {
            nextButton.visible = visible;
            nextButton.active = visible;
        }
    }

    private GraphNode selectedNode() {
        if (menu.getNode() == null || session.snapshot == null) return null;
        UUID id = menu.getNode().getUUID();
        return session.snapshot.nodes().stream().filter(node -> node.nodeId().equals(id)).findFirst().orElse(null);
    }

    private GraphCanvas canvas() {
        return session.canvas;
    }

    private void updateCanvasBounds() {
        int x = menu.getNode() == null ? 8 : canvasLeft(leftPos);
        canvas().setBounds(x, 38, width - x - 8, height - 78);
    }

    private void updatePanelLayout(long now) {
        boolean transitioning = menu.getNode() != null && panelAnimationStartedAt >= 0;
        if (transitioning) {
            long elapsed = now - panelAnimationStartedAt;
            leftPos = panelLeft(elapsed);
            if (panelAnimationComplete(elapsed)) panelAnimationStartedAt = -1;
        } else {
            leftPos = PANEL_OPEN_LEFT;
        }
        if (previousButton != null) previousButton.setX(leftPos + 6);
        if (nextButton != null) nextButton.setX(leftPos + 232);
        updateCanvasBounds();
        if (transitioning) canvas().fit();
    }

    static int panelLeft(long elapsedMillis) {
        float progress = Math.max(0.0f, Math.min(1.0f, elapsedMillis / (float) PANEL_ANIMATION_MILLIS));
        float remaining = 1.0f - progress;
        float eased = 1.0f - remaining * remaining * remaining;
        return Math.round(PANEL_CLOSED_LEFT + (PANEL_OPEN_LEFT - PANEL_CLOSED_LEFT) * eased);
    }

    static int canvasLeft(int editorLeft) {
        return Math.max(8, editorLeft + PANEL_CANVAS_GAP);
    }

    static boolean panelAnimationComplete(long elapsedMillis) {
        return elapsedMillis >= PANEL_ANIMATION_MILLIS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePanelLayout(Util.getMillis());
        graphics.pose().pushPose();
        graphics.pose().scale((float) uiScale, (float) uiScale, 1);
        super.render(graphics, (int) (mouseX / uiScale), (int) (mouseY / uiScale), partialTick);
        int scaledMouseX = (int) (mouseX / uiScale);
        int scaledMouseY = (int) (mouseY / uiScale);
        if (labelConfirmation == null) canvas().renderTooltips(graphics, font, scaledMouseX, scaledMouseY, theme());
        else labelConfirmation.render(graphics, scaledMouseX, scaledMouseY);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Theme theme = theme();
        graphics.fill(0, 0, width, height, theme.bg());
        int gridColor = 0x28000000 | (theme.border() & 0x00FFFFFF);
        for (int y = 0; y < height; y += 8) {
            graphics.hLine(0, width, y, gridColor);
        }
        updateCanvasBounds();
        int canvasX = menu.getNode() == null ? 8 : canvasLeft(leftPos);
        graphics.enableScissor((int) (canvasX * uiScale), (int) (38 * uiScale),
                (int) ((width - 8) * uiScale), (int) ((height - 40) * uiScale));
        canvas().render(graphics, font, mouseX, mouseY, theme, Util.getMillis());
        graphics.disableScissor();
        drawChrome(graphics, theme);
        canvas().drawLegend(graphics, font, menu.getNode() == null ? 12 : canvasX, height - 31,
                mouseX, mouseY, theme);
        if (menu.getNode() != null) {
            graphics.fill(leftPos - 4, 36, leftPos + 262, height - 4, theme.surface());
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            drawMember(graphics, mouseX, mouseY, theme);
        }
    }

    private void drawChrome(GuiGraphics graphics, Theme theme) {
        graphics.fill(0, 0, width, 34, theme.surface());
        int unloaded = session.snapshot == null ? 0 : session.snapshot.totalNodes() - session.snapshot.nodes().size();
        Component status = session.snapshot == null ? text("loading") : unloaded > 0
                ? Component.translatable("gui.logisticsnetworks.graph.unloaded", unloaded)
                : session.snapshot.nodes().isEmpty() ? text("empty") : text("hint");
        graphics.drawString(font, status, menu.getNode() == null ? 12 : canvasLeft(leftPos), height - 15,
                theme.textMuted(), false);
    }

    private void drawMember(GuiGraphics graphics, int mouseX, int mouseY, Theme theme) {
        GraphNode selected = selectedNode();
        if (selected == null) return;
        List<GraphNode> members = selectedMembers();
        int index = members.indexOf(selected) + 1;
        int selectedCount = canvas().getSelectedNodes().size();
        Component heading = selectedCount > 1
                ? Component.translatable("gui.logisticsnetworks.graph.selected_nodes", selectedCount,
                        selected.blockName())
                : Component.translatable("gui.logisticsnetworks.graph.selected_member", index, members.size());
        ThemePaint.drawCentered(graphics, font, heading, leftPos + 128, topPos - font.lineHeight - 2, theme.text());
        if (!selected.label().isEmpty() && topPos > 70) {
            graphics.drawString(font, text("shared"), leftPos + 6, 44, theme.textMuted(), false);
        }
        if (mouseX >= leftPos + 28 && mouseX < leftPos + 228
                && mouseY >= topPos - 26 && mouseY < topPos - 6) {
            graphics.renderComponentTooltip(font, List.of(Component.literal(selected.blockName()),
                    Component.literal(selected.dimension() + " " + selected.attachedPos().toShortString()),
                    text(selected.label().isEmpty() ? "individual" : "shared")), mouseX, mouseY);
        }
    }

    private List<GraphNode> selectedMembers() {
        String key = canvas().getSelected();
        return canvas().getGraph().vertices().stream().filter(vertex -> vertex.key().equals(key))
                .findFirst().map(NetworkGraph.Vertex::members).orElse(List.of());
    }

    private void selectMember(int direction) {
        List<GraphNode> members = selectedMembers();
        if (members.size() < 2) return;
        int index = members.indexOf(selectedNode());
        openSelection(members.get(Math.floorMod(index + direction, members.size())).nodeId());
    }

    void selectPrimary(UUID nodeId) {
        openSelection(nodeId);
    }

    private void openSelection(UUID nodeId) {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        UUID openNode = menu.getNode() == null ? null : menu.getNode().getUUID();
        if (Objects.equals(openNode, nodeId)) return;
        commitPendingEdits();
        openingSelection = true;
        selectionRequestedAt = System.currentTimeMillis();
        PacketDistributor.sendToServer(new RequestOpenGraphPayload(graphMenu.getComputerPos(),
                graphMenu.getComputerDimension(), graphMenu.getGraphNetworkId(), Optional.ofNullable(nodeId),
                getSelectedChannel()));
    }

    void moveVertices(Map<String, GraphPosition> positions) {
        if (positions.size() > MoveGraphVerticesPayload.MAX_VERTICES) return;
        PacketDistributor.sendToServer(new MoveGraphVerticesPayload(graphMenu.getGraphNetworkId(), positions));
    }

    private List<GraphNode> selectedNodes() {
        if (session.snapshot == null) return List.of();
        Set<UUID> selected = canvas().getSelectedNodes();
        return session.snapshot.nodes().stream().filter(node -> selected.contains(node.nodeId())).toList();
    }

    @Override
    protected void commitLabelChange(String label) {
        if (labelRequestPending) return;
        List<GraphNode> selected = selectedNodes();
        UUID primary = canvas().getPrimaryNode();
        if (selected.isEmpty() || primary == null) return;
        List<UUID> nodeIds = selected.stream().map(GraphNode::nodeId).toList();
        if (nodeIds.size() > SetNodeLabelsPayload.MAX_NODES) return;
        commitPendingEdits();
        labelRequestPending = true;
        labelRequestId = UUID.randomUUID();
        PacketDistributor.sendToServer(new PreviewGraphLabelsPayload(
                labelRequestId, graphMenu.getGraphNetworkId(), nodeIds, label, primary));
    }

    public void receiveLabelPreview(GraphLabelPreviewPayload preview) {
        if (!preview.networkId().equals(graphMenu.getGraphNetworkId())
                || !preview.requestId().equals(labelRequestId)) return;
        labelRequestPending = false;
        if (preview.result() == GraphLabelPreviewPayload.Result.APPLIED
                || preview.result() == GraphLabelPreviewPayload.Result.NONE) {
            labelRequestId = null;
            labelConfirmation = null;
            closeLabelPicker();
        } else if (preview.result() == GraphLabelPreviewPayload.Result.CONFIRM) {
            labelConfirmation = new LabelConfirmation(preview);
        } else {
            labelRequestId = null;
            labelConfirmation = null;
        }
    }

    private void confirmLabel(GraphLabelPreviewPayload preview) {
        if (labelRequestPending) return;
        labelRequestPending = true;
        PacketDistributor.sendToServer(new ConfirmGraphLabelsPayload(preview.requestId(), preview.networkId(),
                preview.nodeIds(), preview.label(), preview.settingsSource(), preview.expectedState()));
    }

    private void resetLayout() {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        PacketDistributor.sendToServer(new ResetGraphLayoutPayload(graphMenu.getGraphNetworkId()));
    }

    private void returnToComputer() {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        commitPendingEdits();
        NodeGraphSession.returnToComputer(graphMenu.getGraphNetworkId());
        PacketDistributor.sendToServer(new ReturnToComputerPayload(graphMenu.getGraphNetworkId()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (labelRequestPending) return true;
        if (labelConfirmation != null) {
            labelConfirmation.mouseClicked(x, y, button);
            return true;
        }
        if (isStorageUpgradePickerOpen()) return super.mouseClicked(x, y, button);
        if (openingSelection) return true;
        if (canvas().legendMouseClicked(x, y, button, Util.getMillis())) return true;
        for (var child : children()) {
            if (child instanceof Button control && control.mouseClicked(x, y, button)) {
                setFocused(control);
                return true;
            }
        }
        if (!menu.getCarried().isEmpty() && (menu.getNode() == null || x >= 270)) return true;
        if (canvas().mouseClicked(x, y, button, hasControlDown(), hasShiftDown())) return true;
        return menu.getNode() != null && super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (labelRequestPending || labelConfirmation != null) return true;
        if (isStorageUpgradePickerOpen()) {
            return super.mouseDragged(x, y, button, deltaX / uiScale, deltaY / uiScale);
        }
        if (canvas().mouseDragged(x, y, button, deltaX / uiScale, deltaY / uiScale)) return true;
        return menu.getNode() != null && super.mouseDragged(x, y, button, deltaX / uiScale, deltaY / uiScale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (labelRequestPending || labelConfirmation != null) return true;
        if (isStorageUpgradePickerOpen()) return super.mouseReleased(x, y, button);
        if (canvas().mouseReleased(x, y, button)) return true;
        return menu.getNode() != null && super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (labelRequestPending || labelConfirmation != null) return true;
        if (isStorageUpgradePickerOpen()) return super.mouseScrolled(x, y, deltaX, deltaY);
        if (canvas().mouseScrolled(x, y, deltaY)) return true;
        return menu.getNode() != null && super.mouseScrolled(x, y, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (labelRequestPending) {
            if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (labelConfirmation != null) {
            labelConfirmation.keyPressed(key);
            return true;
        }
        if (isStorageUpgradePickerOpen()) return super.keyPressed(key, scan, modifiers);
        if (openingSelection) return true;
        if (key == 258 || getFocused() instanceof Button) return handleScreenKey(key, scan, modifiers);
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return labelRequestPending || labelConfirmation != null || super.charTyped(character, modifiers);
    }

    @Override
    protected boolean handleInteraction(double mouseX, double mouseY, int action) {
        return menu.getNode() != null && super.handleInteraction(mouseX, mouseY, action);
    }

    @Override
    protected double editorMouseX() {
        return super.editorMouseX() / uiScale;
    }

    @Override
    protected double editorMouseY() {
        return super.editorMouseY() / uiScale;
    }

    @Override
    public void onClose() {
        returnToComputer();
    }

    @Override
    public Rect2i getFilterSlotArea(int slot) {
        Rect2i area = super.getFilterSlotArea(slot);
        return new Rect2i((int) (area.getX() * uiScale), (int) (area.getY() * uiScale),
                (int) Math.ceil(area.getWidth() * uiScale), (int) Math.ceil(area.getHeight() * uiScale));
    }

    private static Component text(String key) {
        return Component.translatable("gui.logisticsnetworks.graph." + key);
    }

    public static void clearSession() {
        NodeGraphSession.clear();
    }

    private final class LabelConfirmation {
        private static final int WIDTH = 280;
        private static final int HEIGHT = 96;
        private static final int BUTTON_WIDTH = 126;
        private final GraphLabelPreviewPayload preview;

        private LabelConfirmation(GraphLabelPreviewPayload preview) {
            this.preview = preview;
        }

        private void render(GuiGraphics graphics, int mouseX, int mouseY) {
            int x = (width - WIDTH) / 2;
            int y = (height - HEIGHT) / 2;
            Theme currentTheme = theme();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            graphics.fill(0, 0, width, height, 0x88000000);
            ThemePaint.window(graphics, x, y, WIDTH, HEIGHT, currentTheme);
            graphics.drawWordWrap(font, confirmationMessage(), x + 10, y + 10, WIDTH - 20, currentTheme.text());
            int buttonY = y + HEIGHT - 26;
            ThemePaint.button(graphics, font, x + 10, buttonY, BUTTON_WIDTH, 18,
                    Component.translatable("gui.cancel").getString(), inside(mouseX, mouseY, x + 10), currentTheme);
            ThemePaint.button(graphics, font, x + 144, buttonY, BUTTON_WIDTH, 18,
                    Component.translatable("gui.logisticsnetworks.graph.confirm.apply",
                            preview.nodeIds().size()).getString(),
                    inside(mouseX, mouseY, x + 144), currentTheme);
            graphics.pose().popPose();
        }

        private Component confirmationMessage() {
            return switch (preview.kind()) {
                case REMOVE -> Component.translatable("gui.logisticsnetworks.graph.confirm.remove",
                        preview.nodeIds().size());
                case JOIN -> Component.translatable(preview.replacedLabels() == 0
                                ? "gui.logisticsnetworks.graph.confirm.join"
                                : "gui.logisticsnetworks.graph.confirm.join_replace",
                        preview.label(), preview.nodeIds().size());
                case REPLACE -> Component.translatable("gui.logisticsnetworks.graph.confirm.replace",
                        preview.label(), preview.nodeIds().size(), preview.replacedLabels());
                default -> Component.empty();
            };
        }

        private void mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return;
            int x = (width - WIDTH) / 2;
            if (inside(mouseX, mouseY, x + 10)) cancel();
            else if (inside(mouseX, mouseY, x + 144)) confirm();
        }

        private void keyPressed(int key) {
            if (key == GLFW.GLFW_KEY_ESCAPE) cancel();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm();
        }

        private void cancel() {
            labelRequestId = null;
            labelConfirmation = null;
        }

        private void confirm() {
            confirmLabel(preview);
        }

        private boolean inside(double mouseX, double mouseY, int buttonX) {
            int buttonY = (height - HEIGHT) / 2 + HEIGHT - 26;
            return mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + 18;
        }
    }

    private static final class GraphButton extends Button {
        private final Supplier<Theme> theme;

        private GraphButton(int x, int y, int width, Component message, Runnable action,
                            Supplier<Theme> theme) {
            super(x, y, width, 20, message, button -> action.run(), DEFAULT_NARRATION);
            this.theme = theme;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ThemePaint.button(graphics, Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(),
                    getMessage().getString(), isHoveredOrFocused(), theme.get());
        }
    }

    private static final class GraphIconButton extends Button {
        private final Supplier<Theme> theme;
        private final ResourceLocation icon;

        private GraphIconButton(int x, int y, Component message, Runnable action, Supplier<Theme> theme,
                                ResourceLocation icon) {
            super(x, y, 18, 18, message, button -> action.run(), DEFAULT_NARRATION);
            this.theme = theme;
            this.icon = icon;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Theme currentTheme = theme.get();
            int color = isHoveredOrFocused() ? currentTheme.text() : currentTheme.accent();
            float red = (color >> 16 & 0xFF) / 255.0f;
            float green = (color >> 8 & 0xFF) / 255.0f;
            float blue = (color & 0xFF) / 255.0f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(red, green, blue, 1.0f);
            graphics.blit(icon, getX() + 1, getY() + 1, 16, 16, 0, 0, 512, 512, 512, 512);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }
}
