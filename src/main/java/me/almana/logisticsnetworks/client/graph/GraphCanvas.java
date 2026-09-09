package me.almana.logisticsnetworks.client.graph;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import net.minecraft.client.gui.Font;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class GraphCanvas {
    private static final int NODE_RADIUS = 22;
    private static final int NODE_HALF_HEIGHT = 19;
    private static final int DRAG_THRESHOLD = 4;
    private static final int MAX_TOOLTIP_MEMBERS = 8;
    private static final int MAX_TOOLTIP_ROUTES = 10;
    private final Consumer<String> selectionChanged;
    private final BiConsumer<String, GraphPosition> positionMoved;
    private final GraphCamera camera = new GraphCamera();
    private final EnumMap<ChannelType, ConnectionBatch> connections = new EnumMap<>(ChannelType.class);
    private final Map<String, List<ConnectionRef>> connectionRefs = new HashMap<>();
    private List<GraphNode> sourceNodes = List.of();
    private NetworkGraph graph = NetworkGraph.from(List.of());
    private Map<String, GraphPosition> positions = new LinkedHashMap<>();
    private String selected;
    private UUID selectedMember;
    private String pressedNode;
    private GraphPosition pressedPosition;
    private double pressX;
    private double pressY;
    private double lastMouseX;
    private double lastMouseY;
    private boolean draggingNode;
    private boolean panning;
    private boolean interactionMoved;
    private boolean fitPending;

    public GraphCanvas(Consumer<String> selectionChanged,
                       BiConsumer<String, GraphPosition> positionMoved) {
        this.selectionChanged = selectionChanged;
        this.positionMoved = positionMoved;
        for (ChannelType type : ChannelType.values()) {
            connections.put(type, new ConnectionBatch(0));
        }
    }

    public void update(List<GraphNode> nodes, Map<String, GraphPosition> savedPositions) {
        List<GraphNode> nextNodes = List.copyOf(nodes);
        if (nextNodes.equals(sourceNodes)) {
            if (applySavedPositions(savedPositions)) {
                rebuildConnections();
            }
            return;
        }

        NetworkGraph oldGraph = graph;
        Map<String, GraphPosition> oldPositions = positions;
        boolean firstGraph = sourceNodes.isEmpty() && !nextNodes.isEmpty();
        sourceNodes = nextNodes;
        graph = NetworkGraph.from(nextNodes);

        Map<String, GraphPosition> nextPositions = new LinkedHashMap<>();
        copySavedPositions(savedPositions, nextPositions);
        copyStablePositions(oldPositions, nextPositions);
        inheritRegroupedPositions(oldGraph, oldPositions, nextPositions);
        positions = new LinkedHashMap<>(NetworkGraph.initialPositions(graph.vertices(), nextPositions));
        positions.keySet().retainAll(vertexKeys());
        if (pressedNode != null && !positions.containsKey(pressedNode)) {
            cancelInteraction();
        }
        if (selected != null && !positions.containsKey(selected)) {
            selected = null;
            selectedMember = null;
        }
        rebuildConnections();
        if (firstGraph) {
            fitPending = true;
            fitIfReady();
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        camera.setBounds(x, y, width, height);
        fitIfReady();
    }

    public void setSelected(String selected, UUID selectedMember) {
        this.selected = selected != null && positions.containsKey(selected) ? selected : null;
        this.selectedMember = this.selected == null ? null : selectedMember;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, Theme theme, long elapsedMillis) {
        if (camera.width() <= 0 || camera.height() <= 0) {
            return;
        }

        String hovered = findNode(mouseX, mouseY);
        GuiGraphics.Pose pose = graphics.pose();
        pose.pushPose();
        pose.translate(camera.x() + camera.width() / 2.0f, camera.y() + camera.height() / 2.0f, 0.0f);
        pose.scale(camera.zoom(), camera.zoom(), 1.0f);
        pose.translate(-camera.centerX(), -camera.centerY(), 0.0f);
        GraphRenderState.Builder vertices = new GraphRenderState.Builder();
        drawConnections(vertices, theme, elapsedMillis);
        drawNodes(graphics, vertices, font, hovered, theme);
        pose.popPose();

    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY, Theme theme) {
        String hovered = findNode(mouseX, mouseY);
        if (hovered != null) {
            renderTooltipDetails(graphics, font, hovered, mouseX, mouseY, theme);
        }
    }

    public void drawLegend(GuiGraphics graphics, Font font, int x, int y, Theme theme) {
        int columnX = x;
        for (ChannelType type : ChannelType.values()) {
            Component label = channelName(type);
            graphics.fill(columnX, y + 3, columnX + 12, y + 5, color(type));
            graphics.drawString(font, label, columnX + 17, y, theme.textMuted(), false);
            columnX += font.width(label) + 27;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !camera.contains(mouseX, mouseY)) {
            return false;
        }

        pressX = mouseX;
        pressY = mouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        pressedNode = findNode(mouseX, mouseY);
        pressedPosition = pressedNode == null ? null : positions.get(pressedNode);
        draggingNode = false;
        interactionMoved = false;
        panning = pressedNode == null;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || (!panning && pressedNode == null)) {
            return false;
        }

        double deltaX = mouseX - pressX;
        double deltaY = mouseY - pressY;
        if (!interactionMoved && deltaX * deltaX + deltaY * deltaY < DRAG_THRESHOLD * DRAG_THRESHOLD) {
            return true;
        }
        interactionMoved = true;

        if (panning) {
            camera.pan(mouseX - lastMouseX, mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        draggingNode = true;
        positions.put(pressedNode, new GraphPosition(
                pressedPosition.x() + (float) deltaX / camera.zoom(),
                pressedPosition.y() + (float) deltaY / camera.zoom()));
        updateConnections(pressedNode);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || (!panning && pressedNode == null)) {
            return false;
        }

        if (!interactionMoved) {
            setSelection(pressedNode);
        } else if (draggingNode) {
            positionMoved.accept(pressedNode, positions.get(pressedNode));
        }
        pressedNode = null;
        pressedPosition = null;
        draggingNode = false;
        panning = false;
        interactionMoved = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!camera.contains(mouseX, mouseY) || amount == 0.0) {
            return false;
        }
        camera.zoomAt(mouseX, mouseY, amount);
        return true;
    }

    public void fit() {
        camera.fit(positions.values());
        fitPending = false;
    }

    public void reset() {
        camera.reset();
    }

    public NetworkGraph getGraph() {
        return graph;
    }

    public String getSelected() {
        return selected;
    }

    public GraphPosition getPosition(String key) {
        return positions.get(key);
    }

    private boolean applySavedPositions(Map<String, GraphPosition> savedPositions) {
        boolean changed = false;
        for (Map.Entry<String, GraphPosition> entry : savedPositions.entrySet()) {
            if (!skipSavedPosition(entry.getKey()) && positions.containsKey(entry.getKey())
                    && !entry.getValue().equals(positions.get(entry.getKey()))) {
                positions.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private void copySavedPositions(Map<String, GraphPosition> savedPositions,
                                    Map<String, GraphPosition> target) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = savedPositions.get(vertex.key());
            if (position != null && !skipSavedPosition(vertex.key())) {
                target.put(vertex.key(), position);
            }
        }
    }

    private void copyStablePositions(Map<String, GraphPosition> oldPositions,
                                     Map<String, GraphPosition> target) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (!target.containsKey(vertex.key()) && oldPositions.containsKey(vertex.key())) {
                target.put(vertex.key(), oldPositions.get(vertex.key()));
            }
        }
    }

    private void inheritRegroupedPositions(NetworkGraph oldGraph,
                                           Map<String, GraphPosition> oldPositions,
                                           Map<String, GraphPosition> target) {
        Map<UUID, GraphPosition> memberPositions = new HashMap<>();
        for (NetworkGraph.Vertex vertex : oldGraph.vertices()) {
            GraphPosition position = oldPositions.get(vertex.key());
            if (position == null) {
                continue;
            }
            for (GraphNode member : vertex.members()) {
                memberPositions.put(member.nodeId(), position);
            }
        }

        Map<GraphPosition, Integer> inheritedCounts = new HashMap<>();
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (target.containsKey(vertex.key())) {
                continue;
            }
            GraphPosition inherited = averageMemberPosition(vertex, memberPositions);
            if (inherited == null) {
                continue;
            }
            int splitIndex = inheritedCounts.merge(inherited, 1, Integer::sum) - 1;
            target.put(vertex.key(), offsetSplit(inherited, splitIndex));
        }
    }

    private GraphPosition averageMemberPosition(NetworkGraph.Vertex vertex,
                                                Map<UUID, GraphPosition> memberPositions) {
        float totalX = 0.0f;
        float totalY = 0.0f;
        int count = 0;
        for (GraphNode member : vertex.members()) {
            GraphPosition position = memberPositions.get(member.nodeId());
            if (position != null) {
                totalX += position.x();
                totalY += position.y();
                count++;
            }
        }
        return count == 0 ? null : new GraphPosition(totalX / count, totalY / count);
    }

    private GraphPosition offsetSplit(GraphPosition position, int index) {
        if (index == 0) {
            return position;
        }
        double angle = (index - 1) * Math.PI / 3.0;
        float distance = 46.0f * (1 + (index - 1) / 6);
        return new GraphPosition(position.x() + (float) Math.cos(angle) * distance,
                position.y() + (float) Math.sin(angle) * distance);
    }

    private Set<String> vertexKeys() {
        Set<String> keys = new HashSet<>(graph.vertices().size());
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            keys.add(vertex.key());
        }
        return keys;
    }

    private void rebuildConnections() {
        connectionRefs.clear();
        EnumMap<ChannelType, Integer> capacities = new EnumMap<>(ChannelType.class);
        for (ChannelType type : ChannelType.values()) {
            capacities.put(type, 0);
        }
        for (NetworkGraph.Edge edge : graph.edges()) {
            capacities.put(edge.type(), capacities.get(edge.type()) + 1);
        }
        for (ChannelType type : ChannelType.values()) {
            connections.put(type, new ConnectionBatch(capacities.get(type)));
        }

        List<NetworkGraph.Edge> edges = graph.edges();
        int runStart = 0;
        while (runStart < edges.size()) {
            NetworkGraph.Edge first = edges.get(runStart);
            int runEnd = runStart + 1;
            while (runEnd < edges.size() && sameRoute(first, edges.get(runEnd))) {
                runEnd++;
            }
            for (int i = runStart; i < runEnd; i++) {
                NetworkGraph.Edge edge = edges.get(i);
                GraphPosition source = positions.get(edge.source());
                GraphPosition target = positions.get(edge.target());
                if (source == null || target == null) {
                    continue;
                }
                float offset = (i - runStart - (runEnd - runStart - 1) / 2.0f) * 5.0f;
                ConnectionBatch batch = connections.get(edge.type());
                int index = batch.add(source, target, offset, NODE_RADIUS);
                ConnectionRef ref = new ConnectionRef(edge.type(), index, edge.source(), edge.target(), offset);
                connectionRefs.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(ref);
                connectionRefs.computeIfAbsent(edge.target(), key -> new ArrayList<>()).add(ref);
            }
            runStart = runEnd;
        }
    }

    private void drawConnections(GraphRenderState.Builder vertices, Theme theme, long elapsedMillis) {
        int casingColor = 0xD0000000 | (theme.borderStrong() & 0x00FFFFFF);
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawCasing(vertices, camera, casingColor);
        }
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawSignal(vertices, camera, color(type));
        }
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawPulses(vertices, camera, color(type), theme.glow(), elapsedMillis,
                    type.ordinal() * 23);
        }
    }

    private boolean sameRoute(NetworkGraph.Edge left, NetworkGraph.Edge right) {
        return left.source().equals(right.source()) && left.target().equals(right.target());
    }

    private void updateConnections(String key) {
        for (ConnectionRef ref : connectionRefs.getOrDefault(key, List.of())) {
            connections.get(ref.type()).update(ref.index(), positions.get(ref.source()), positions.get(ref.target()),
                    ref.offset(), NODE_RADIUS);
        }
    }

    private void drawNodes(GuiGraphics graphics, GraphRenderState.Builder vertices, Font font, String hovered, Theme theme) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = positions.get(vertex.key());
            if (position == null || !nodeVisible(position)) {
                continue;
            }
            int centerX = Math.round(position.x());
            int centerY = Math.round(position.y());
            int fill = vertex.key().equals(hovered) ? theme.accentSoft() : theme.surface2();
            drawHexagon(vertices, centerX, centerY, fill,
                    vertex.key().equals(selected) ? theme.accent() : theme.borderStrong());
        }
        vertices.submit(graphics.raw());
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = positions.get(vertex.key());
            if (position == null || !nodeVisible(position)) {
                continue;
            }
            int centerX = Math.round(position.x());
            int centerY = Math.round(position.y());
            boolean focused = vertex.key().equals(selected) || vertex.key().equals(hovered);
            ItemStack icon = iconFor(vertex, selectedMember);
            if (!icon.isEmpty() && (camera.zoom() >= 0.35f || focused)) {
                graphics.renderItem(icon, centerX - 8, centerY - 8);
            }
            if ((camera.zoom() >= 0.45f || focused) && vertex.key().startsWith("label:")) {
                String label = font.plainSubstrByWidth(vertex.label(), 84);
                int labelX = centerX - font.width(label) / 2;
                graphics.drawString(font, label, labelX, centerY + NODE_HALF_HEIGHT + 4, theme.text(), false);
            }
        }
    }

    static ItemStack iconFor(NetworkGraph.Vertex vertex, UUID selectedMember) {
        GraphNode member = vertex.members().getFirst();
        for (GraphNode candidate : vertex.members()) {
            if (candidate.nodeId().equals(selectedMember)) {
                member = candidate;
                break;
            }
        }
        Identifier blockId = Identifier.parse(member.blockName());
        var item = BuiltInRegistries.BLOCK.getValue(blockId).asItem();
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private boolean nodeVisible(GraphPosition position) {
        float radius = (NODE_RADIUS + 8) * camera.zoom();
        float centerX = camera.screenX(position.x());
        float centerY = camera.screenY(position.y());
        return centerX + radius >= camera.x() && centerX - radius < camera.x() + camera.width()
                && centerY + radius >= camera.y() && centerY - radius < camera.y() + camera.height();
    }

    private void drawHexagon(GraphRenderState.Builder vertices, int centerX, int centerY, int fill, int border) {
        addHexagon(vertices, centerX, centerY, NODE_RADIUS, NODE_HALF_HEIGHT, border);
        addHexagon(vertices, centerX, centerY, NODE_RADIUS - 2, NODE_HALF_HEIGHT - 2, fill);
    }

    private void addHexagon(GraphRenderState.Builder vertices, int centerX, int centerY,
                            int radius, int halfHeight, int color) {
        int shoulder = radius / 2;
        addQuad(vertices, centerX - shoulder, centerY - halfHeight,
                centerX + shoulder, centerY - halfHeight, centerX + radius, centerY,
                centerX - radius, centerY, color);
        addQuad(vertices, centerX - radius, centerY, centerX + radius, centerY,
                centerX + shoulder, centerY + halfHeight, centerX - shoulder, centerY + halfHeight, color);
    }

    private void addQuad(GraphRenderState.Builder vertices,
                         float x1, float y1, float x2, float y2,
                         float x3, float y3, float x4, float y4, int color) {
        vertices.vertex(x4, y4, color);
        vertices.vertex(x3, y3, color);
        vertices.vertex(x2, y2, color);
        vertices.vertex(x1, y1, color);
    }

    private String findNode(double mouseX, double mouseY) {
        if (!camera.contains(mouseX, mouseY)) {
            return null;
        }
        float worldX = camera.worldX(mouseX);
        float worldY = camera.worldY(mouseY);
        List<NetworkGraph.Vertex> vertices = graph.vertices();
        for (int i = vertices.size() - 1; i >= 0; i--) {
            NetworkGraph.Vertex vertex = vertices.get(i);
            GraphPosition position = positions.get(vertex.key());
            if (position != null && insideHexagon(worldX - position.x(), worldY - position.y())) {
                return vertex.key();
            }
        }
        return null;
    }

    private boolean insideHexagon(float x, float y) {
        float absoluteX = Math.abs(x);
        float absoluteY = Math.abs(y);
        return absoluteY <= NODE_HALF_HEIGHT
                && absoluteX <= NODE_RADIUS - absoluteY * (NODE_RADIUS / 2.0f) / NODE_HALF_HEIGHT;
    }

    private void setSelection(String key) {
        if (key == null ? selected == null : key.equals(selected)) {
            return;
        }
        selected = key;
        selectionChanged.accept(key);
    }

    private boolean skipSavedPosition(String key) {
        return draggingNode && key.equals(pressedNode);
    }

    private void cancelInteraction() {
        pressedNode = null;
        pressedPosition = null;
        draggingNode = false;
        panning = false;
        interactionMoved = false;
    }

    private void renderTooltipDetails(GuiGraphics graphics, Font font, String key, int mouseX, int mouseY,
                                      Theme theme) {
        NetworkGraph.Vertex vertex = vertex(key);
        if (vertex == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        String title = vertex.label().isEmpty() ? vertex.members().getFirst().blockName() : vertex.label();
        lines.add(Component.literal(title).withColor(rgb(theme.accent())));
        lines.add(Component.translatable("gui.logisticsnetworks.graph.members", vertex.members().size())
                .withColor(rgb(theme.textMuted())));
        int shown = Math.min(vertex.members().size(), MAX_TOOLTIP_MEMBERS);
        Set<GraphChannel> routes = new LinkedHashSet<>();
        for (int i = 0; i < shown; i++) {
            GraphNode member = vertex.members().get(i);
            lines.add(Component.translatable("gui.logisticsnetworks.graph.member_detail",
                    member.blockName(), member.attachedPos().getX(), member.attachedPos().getY(),
                    member.attachedPos().getZ(), member.dimension().toString()).withColor(rgb(theme.text())));
            if (member.dimensional()) {
                lines.add(Component.translatable("gui.logisticsnetworks.graph.dimensional")
                        .withColor(rgb(theme.info())));
            }
            routes.addAll(member.channels());
        }
        for (int i = shown; i < vertex.members().size(); i++) {
            routes.addAll(vertex.members().get(i).channels());
        }
        if (vertex.members().size() > shown) {
            lines.add(Component.translatable("gui.logisticsnetworks.graph.more_members",
                    vertex.members().size() - shown).withColor(rgb(theme.textMuted())));
        }
        if (!routes.isEmpty()) {
            lines.add(Component.translatable("gui.logisticsnetworks.graph.routes", routes.size())
                    .withColor(rgb(theme.textMuted())));
            int routeIndex = 0;
            for (GraphChannel route : routes) {
                if (routeIndex++ >= MAX_TOOLTIP_ROUTES) {
                    break;
                }
                lines.add(Component.translatable("gui.logisticsnetworks.graph.route", route.index() + 1,
                        channelName(route.type()), modeName(route.mode())).withColor(rgb(theme.textSubtle())));
            }
            if (routes.size() > MAX_TOOLTIP_ROUTES) {
                lines.add(Component.translatable("gui.logisticsnetworks.graph.more_routes",
                        routes.size() - MAX_TOOLTIP_ROUTES).withColor(rgb(theme.textMuted())));
            }
        }
        drawTooltip(graphics, font, lines, mouseX, mouseY, theme);
    }

    private void drawTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY,
                             Theme theme) {
        int contentWidth = 0;
        for (Component line : lines) contentWidth = Math.max(contentWidth, font.width(line));
        int boxWidth = contentWidth + 8;
        int boxHeight = lines.size() * 10 + 6;
        int x = mouseX + 12;
        if (x + boxWidth > camera.x() + camera.width() - 4) x = mouseX - boxWidth - 4;
        x = Math.max(camera.x() + 4, x);
        int y = Math.max(camera.y() + 4, mouseY - 12);
        if (y + boxHeight > camera.y() + camera.height() - 4) {
            y = Math.max(camera.y() + 4, camera.y() + camera.height() - boxHeight - 4);
        }
        ThemePaint.window(graphics, x, y, boxWidth, boxHeight, theme);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 4, y + 4 + i * 10, theme.text(), false);
        }
    }

    private NetworkGraph.Vertex vertex(String key) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (vertex.key().equals(key)) {
                return vertex;
            }
        }
        return null;
    }

    private void fitIfReady() {
        if (fitPending && camera.width() > 0 && camera.height() > 0) {
            fit();
        }
    }

    private static Component channelName(ChannelType type) {
        return Component.translatable("gui.logisticsnetworks.graph.channel."
                + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component modeName(ChannelMode mode) {
        return Component.translatable("gui.logisticsnetworks.graph.mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static int color(ChannelType type) {
        return switch (type) {
            case ITEM -> 0xFFF2B84B;
            case FLUID -> 0xFF39CFE8;
            case ENERGY -> 0xFFE15B5B;
            case CHEMICAL -> 0xFF52C77A;
            case SOURCE -> 0xFFA56CE3;
        };
    }

    private static int rgb(int color) {
        return color & 0x00FFFFFF;
    }

    private record ConnectionRef(ChannelType type, int index, String source, String target, float offset) {
    }

}
