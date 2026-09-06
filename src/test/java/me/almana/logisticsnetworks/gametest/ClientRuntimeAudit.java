package me.almana.logisticsnetworks.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.network.OpenNodeMenuPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Screenshot;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class ClientRuntimeAudit {
    private static final String FIRST = "{\"minecraft:damage\":5}";
    private static final String SECOND = "{\"minecraft:damage\":6}";
    private static final String THIRD = "{\"minecraft:damage\":7}";
    private static final String FOURTH = "{\"minecraft:damage\":8}";
    private static Stage stage = Stage.BOOT;
    private static int ticks;
    private static UUID playerId;
    private static CompletableFuture<String> serverCheck;
    private static String screenshotResult;
    private static String screenshotName = "parity-filter-raw-reopened.png";
    private static Path modelScreenshot;
    private static String failure;
    private static int nodeId;
    private static boolean modalKeyPreserved;
    private static boolean nodeKeyOpenedFilter;
    private static boolean normalKeyOpenedFilter;
    private static String firstScanMessage;
    private static String secondScanMessage;
    private static String finalRaw;
    private static int filterMenuId;
    private static boolean editorNameTyped;
    private static int serverRetries;
    private static int modelSettleTicks;

    private ClientRuntimeAudit() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!"client".equals(System.getProperty("logisticsnetworks.parityAudit"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks < 3) {
                return;
            }
            ticks = 0;
            advance(mc);
        } catch (Throwable throwable) {
            fail(mc, throwable);
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if ((stage != Stage.CAPTURE && stage != Stage.EDITOR_CAPTURE
                && stage != Stage.NODE_EDITOR_ASSERT_CAPTURE && stage != Stage.MODEL_CAPTURE)
                || screenshotResult != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        screenshotResult = "pending";
        Screenshot.grab(mc.gameDirectory, screenshotName, mc.getMainRenderTarget(), 1,
                message -> mc.execute(() -> screenshotResult = message.getString()));
    }

    private static void advance(Minecraft mc) throws Exception {
        System.out.println("PARITY_CLIENT stage=" + stage + " screen="
                + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
        switch (stage) {
            case BOOT -> {
                if (mc.getOverlay() != null) {
                    return;
                }
                Screen parent = mc.screen != null ? mc.screen : new TitleScreen();
                stage = Stage.WORLD;
                mc.createWorldOpenFlows().createFreshLevel("parity-client",
                        new LevelSettings("Parity client audit", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                                WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0L, false, false), WorldPresets::createFlatWorldDimensions, parent);
            }
            case WORLD -> {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
                    return;
                }
                playerId = mc.player.getUUID();
                serverCheck = mc.getSingleplayerServer().submit(() -> seed(mc));
                stage = Stage.SEEDED;
            }
            case SEEDED -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                require("seeded".equals(serverCheck.join()), serverCheck.join());
                if (!mc.player.getInventory().getItem(0).is(Registration.SMALL_FILTER.get())) {
                    return;
                }
                ClientPacketDistributor.sendToServer(new OpenFilterInSlotPayload(0));
                stage = Stage.MENU;
            }
            case MENU -> {
                if (!(mc.screen instanceof FilterScreen screen) || !(mc.player.containerMenu instanceof FilterMenu)) {
                    return;
                }
                require(mc.isWindowActive(), "window inactive");
                clickEntryDetail(mc, screen);
                stage = Stage.DETAIL;
            }
            case DETAIL -> {
                FilterScreen screen = screen(mc);
                require(intField(screen, "detailEditSlot") == 0, "detail slot not opened");
                clickConfigure(mc, screen);
                stage = Stage.NBT_TABLE;
            }
            case NBT_TABLE -> {
                FilterScreen screen = screen(mc);
                require(booleanField(screen, "detailNbtPageOpen"), "NBT page not opened");
                clickRawToggle(mc, screen);
                stage = Stage.NBT_RAW;
            }
            case NBT_RAW -> {
                FilterScreen screen = screen(mc);
                require(booleanField(screen, "detailNbtRawMode"), "raw mode not opened");
                MultiLineEditBox box = box(screen);
                click(mc, box.getX() + 3, box.getY() + 3);
                stage = Stage.TYPE_OUTSIDE;
            }
            case TYPE_OUTSIDE -> {
                FilterScreen screen = screen(mc);
                MultiLineEditBox box = box(screen);
                require(box.isFocused(), "raw editor not focused");
                replaceText(mc, FIRST);
                clickInsidePanelBorder(mc, screen);
                require(!box(screen).isFocused(), "outside-editor click kept focus");
                require(FIRST.equals(localRaw(screen)), "outside-editor local flush mismatch");
                serverCheck = authoritative(mc, FIRST, 5);
                stage = Stage.CHECK_OUTSIDE;
            }
            case CHECK_OUTSIDE -> {
                if (!serverMatched(mc, FIRST, 5)) {
                    return;
                }
                focusRaw(mc, screen(mc));
                replaceText(mc, SECOND);
                key(mc, InputConstants.KEY_RETURN, 0);
                require(SECOND.equals(localRaw(screen(mc))), "return local flush mismatch");
                serverCheck = authoritative(mc, SECOND, 6);
                stage = Stage.CHECK_RETURN;
            }
            case CHECK_RETURN -> {
                if (!serverMatched(mc, SECOND, 6)) {
                    return;
                }
                focusRaw(mc, screen(mc));
                replaceText(mc, THIRD);
                key(mc, InputConstants.KEY_NUMPADENTER, 0);
                require(THIRD.equals(localRaw(screen(mc))), "numpad local flush mismatch");
                serverCheck = authoritative(mc, THIRD, 7);
                stage = Stage.CHECK_NUMPAD;
            }
            case CHECK_NUMPAD -> {
                if (!serverMatched(mc, THIRD, 7)) {
                    return;
                }
                focusRaw(mc, screen(mc));
                replaceText(mc, FOURTH);
                clickOutsidePanel(mc, screen(mc));
                require(intField(screen(mc), "detailEditSlot") == -1, "outside panel did not close detail");
                serverCheck = authoritative(mc, FOURTH, 8);
                stage = Stage.CHECK_CLOSE;
            }
            case CHECK_CLOSE -> {
                if (!serverMatched(mc, FOURTH, 8)) {
                    return;
                }
                FilterScreen screen = screen(mc);
                clickEntryDetail(mc, screen);
                stage = Stage.REOPEN_DETAIL;
            }
            case REOPEN_DETAIL -> {
                FilterScreen screen = screen(mc);
                clickConfigure(mc, screen);
                stage = Stage.REOPEN_TABLE;
            }
            case REOPEN_TABLE -> {
                FilterScreen screen = screen(mc);
                clickRawToggle(mc, screen);
                stage = Stage.REOPEN_RAW;
            }
            case REOPEN_RAW -> {
                FilterScreen screen = screen(mc);
                require(FOURTH.equals(box(screen).getValue()), "reopened value mismatch");
                finalRaw = localRaw(screen);
                filterMenuId = screen.getMenu().containerId;
                stage = Stage.CAPTURE;
            }
            case CAPTURE -> {
                if (screenshotResult == null || "pending".equals(screenshotResult)) {
                    return;
                }
                Path screenshot = mc.gameDirectory.toPath().resolve("screenshots/parity-filter-raw-reopened.png");
                require(Files.size(screenshot) > 0, "screenshot missing");
                serverCheck = mc.getSingleplayerServer().submit(() -> createNode(mc));
                stage = Stage.NODE_FIXTURE;
            }
            case NODE_FIXTURE -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                String result = serverCheck.join();
                require(result.startsWith("node="), result);
                nodeId = Integer.parseInt(result.substring(5));
                ClientPacketDistributor.sendToServer(new OpenNodeMenuPayload(nodeId, 0));
                stage = Stage.NODE_MENU;
            }
            case NODE_MENU -> {
                if (!(mc.screen instanceof NodeScreen screen) || !(mc.player.containerMenu instanceof NodeMenu)) {
                    return;
                }
                click(mc, screen.getGuiLeft() + 226, screen.getGuiTop() + 10);
                stage = Stage.NODE_NETWORKS;
            }
            case NODE_NETWORKS -> {
                NodeScreen screen = nodeScreen(mc);
                int entryWidth = 228;
                int buttonWidth = mc.font.width(net.minecraft.network.chat.Component.translatable(
                        "gui.logisticsnetworks.node.edit")) + 14;
                int buttonX = screen.getGuiLeft() + 14 + entryWidth - buttonWidth;
                click(mc, buttonX + buttonWidth / 2.0, screen.getGuiTop() + 103);
                stage = Stage.NODE_EDITOR;
            }
            case NODE_EDITOR -> {
                NodeScreen screen = nodeScreen(mc);
                Object editor = field(screen, "editor").get(screen);
                require(editor != null, "network editor not opened");
                ClientControls.SECONDARY_INTERACTION.setKey(
                        InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_X));
                KeyMapping.resetMapping();
                net.minecraft.client.gui.components.EditBox nameField =
                        (net.minecraft.client.gui.components.EditBox) field(editor, "nameField").get(editor);
                String nameBefore = nameField.getValue();
                character(mc, 'x');
                require(nameField.getValue().equals(nameBefore + "x"), "editor name did not receive character");
                require(mc.screen == screen && mc.player.containerMenu instanceof NodeMenu,
                        "editor character changed node menu");
                editorNameTyped = true;
                int editorX = (screen.width - 140) / 2;
                int editorY = (screen.height - 166) / 2;
                click(mc, editorX + 50, editorY + 60);
                require(field(screen, "editor").get(screen) != null, "SV click closed editor");
                screenshotResult = null;
                screenshotName = "parity-node-editor.png";
                stage = Stage.EDITOR_CAPTURE;
            }
            case EDITOR_CAPTURE -> {
                if (screenshotResult == null || "pending".equals(screenshotResult)) {
                    return;
                }
                NodeScreen screen = nodeScreen(mc);
                moveToPlayerFilter(mc, screen);
                stage = Stage.NODE_EDITOR_KEY;
            }
            case NODE_EDITOR_KEY -> {
                key(mc, InputConstants.KEY_X, 0);
                System.out.println("PARITY_CLIENT modal_key screen="
                        + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
                screenshotResult = null;
                screenshotName = "parity-after-modal-key.png";
                stage = Stage.NODE_EDITOR_ASSERT_CAPTURE;
            }
            case NODE_EDITOR_ASSERT_CAPTURE -> {
                if (screenshotResult == null || "pending".equals(screenshotResult)) {
                    return;
                }
                stage = Stage.NODE_EDITOR_ASSERT;
            }
            case NODE_EDITOR_ASSERT -> {
                require(mc.screen instanceof NodeScreen, "modal key changed screen to "
                        + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
                NodeScreen screen = nodeScreen(mc);
                require(field(screen, "editor").get(screen) != null, "secondary key escaped network editor");
                serverCheck = mc.getSingleplayerServer().submit(() -> {
                    ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    return player.containerMenu instanceof NodeMenu ? "node" : player.containerMenu.getClass().getName();
                });
                stage = Stage.NODE_EDITOR_SERVER;
            }
            case NODE_EDITOR_SERVER -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                require("node".equals(serverCheck.join()), "modal key changed server menu " + serverCheck.join());
                modalKeyPreserved = true;
                key(mc, InputConstants.KEY_ESCAPE, 0);
                NodeScreen screen = nodeScreen(mc);
                require(field(screen, "editor").get(screen) == null, "escape did not close editor");
                ClientControls.SECONDARY_INTERACTION.setKey(ClientControls.SECONDARY_INTERACTION.getDefaultKey());
                ClientControls.PRIMARY_INTERACTION.setKey(
                        InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_X));
                KeyMapping.resetMapping();
                ClientPacketDistributor.sendToServer(new OpenNodeMenuPayload(nodeId, 0));
                stage = Stage.NODE_CHANNEL_MENU;
            }
            case NODE_CHANNEL_MENU -> {
                if (!(mc.screen instanceof NodeScreen screen) || !(mc.player.containerMenu instanceof NodeMenu)) {
                    return;
                }
                Object page = field(screen, "currentPage").get(screen);
                if (!"CHANNEL_CONFIG".equals(page.toString())) {
                    return;
                }
                move(mc, screen.getGuiLeft() + 176, screen.getGuiTop() + 76);
                stage = Stage.NODE_FILTER_KEY;
            }
            case NODE_FILTER_KEY -> {
                key(mc, InputConstants.KEY_X, 0);
                stage = Stage.NODE_FILTER_ASSERT;
            }
            case NODE_FILTER_ASSERT -> {
                if (!(mc.screen instanceof FilterScreen screen) || !(mc.player.containerMenu instanceof FilterMenu menu)) {
                    return;
                }
                require(menu.isNodeFilter(), "node key opened inventory filter");
                require(menu.getNodeSource().getId() == nodeId && menu.getNodeChannel() == 0
                        && menu.getNodeFilterSlot() == 0, "node filter route mismatch");
                nodeKeyOpenedFilter = true;
                ClientControls.PRIMARY_INTERACTION.setKey(ClientControls.PRIMARY_INTERACTION.getDefaultKey());
                KeyMapping.resetMapping();
                clickScan(mc, screen);
                serverCheck = scanState(mc);
                stage = Stage.SCAN_FIRST;
            }
            case SCAN_FIRST -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                String result = serverCheck.join();
                if (!"entries=3,items=true".equals(result)) {
                    serverCheck = scanState(mc);
                    return;
                }
                firstScanMessage = overlayMessage(mc);
                if (firstScanMessage.isEmpty()) {
                    return;
                }
                require(firstScanMessage.contains("2"), "first scan status mismatch " + firstScanMessage);
                clickScan(mc, screen(mc));
                serverCheck = scanState(mc);
                stage = Stage.SCAN_SECOND;
            }
            case SCAN_SECOND -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                require("entries=3,items=true".equals(serverCheck.join()), "second scan state " + serverCheck.join());
                secondScanMessage = overlayMessage(mc);
                if (secondScanMessage.equals(firstScanMessage)) {
                    return;
                }
                FilterScreen screen = screen(mc);
                click(mc, screen.getGuiLeft() + 14, screen.getGuiTop() + 11);
                stage = Stage.NODE_RETURN;
            }
            case NODE_RETURN -> {
                if (!(mc.screen instanceof NodeScreen screen) || !(mc.player.containerMenu instanceof NodeMenu)) {
                    return;
                }
                ClientControls.PRIMARY_INTERACTION.setKey(ClientControls.PRIMARY_INTERACTION.getDefaultKey());
                ClientControls.SECONDARY_INTERACTION.setKey(ClientControls.SECONDARY_INTERACTION.getDefaultKey());
                KeyMapping.resetMapping();
                move(mc, screen.getGuiLeft() + 176, screen.getGuiTop() + 76);
                stage = Stage.NODE_REMOVE;
            }
            case NODE_REMOVE -> {
                mouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                serverCheck = nodeFilterState(mc);
                stage = Stage.NODE_REMOVE_ASSERT;
            }
            case NODE_REMOVE_ASSERT -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                if (!"empty".equals(serverCheck.join())) {
                    serverCheck = nodeFilterState(mc);
                    return;
                }
                NodeScreen screen = nodeScreen(mc);
                if (!screen.getMenu().getNode().getChannel(0).getFilterItem(0).isEmpty()) {
                    return;
                }
                ClientControls.SECONDARY_INTERACTION.setKey(
                        InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_X));
                KeyMapping.resetMapping();
                moveToPlayerFilter(mc, screen);
                stage = Stage.NODE_NORMAL_KEY;
            }
            case NODE_NORMAL_KEY -> {
                key(mc, InputConstants.KEY_X, 0);
                stage = Stage.NODE_NORMAL_ASSERT;
            }
            case NODE_NORMAL_ASSERT -> {
                if (!(mc.screen instanceof FilterScreen) || !(mc.player.containerMenu instanceof FilterMenu)) {
                    return;
                }
                normalKeyOpenedFilter = true;
                key(mc, InputConstants.KEY_ESCAPE, 0);
                serverCheck = mc.getSingleplayerServer().submit(() -> prepareModels(mc));
                stage = Stage.MODEL_SETUP;
            }
            case MODEL_SETUP -> {
                if (!serverCheck.isDone()) {
                    return;
                }
                require(serverCheck.join().startsWith("prepared="), serverCheck.join());
                if (!mc.player.getMainHandItem().is(Registration.LOGISTICS_NODE_ITEM.get())) {
                    return;
                }
                require(mc.screen == null, "model scene still has screen " + mc.screen);
                mc.player.setXRot(22.0F);
                modelSettleTicks = 0;
                stage = Stage.MODEL_SETTLE;
            }
            case MODEL_SETTLE -> {
                if (++modelSettleTicks < 8) {
                    return;
                }
                screenshotResult = null;
                screenshotName = "parity-laptop-node-item.png";
                stage = Stage.MODEL_CAPTURE;
            }
            case MODEL_CAPTURE -> {
                if (screenshotResult == null || "pending".equals(screenshotResult)) {
                    return;
                }
                modelScreenshot = mc.gameDirectory.toPath().resolve("screenshots/parity-laptop-node-item.png");
                require(Files.size(modelScreenshot) > 0, "model screenshot missing");
                finish(mc, mc.gameDirectory.toPath().resolve("screenshots/parity-filter-raw-reopened.png"));
            }
            case DONE -> {
            }
        }
    }

    private static String seed(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
        ItemStack filter = Registration.SMALL_FILTER.get().getDefaultInstance();
        FilterItemData.addItem(filter, new ItemStack(Items.DIAMOND_SWORD), player.level().registryAccess());
        FilterItemData.setEntryNbtStrict(filter, 0, false);
        player.getInventory().setItem(0, filter);
        player.getInventory().setChanged();
        player.inventoryMenu.sendAllDataToRemote();
        return "seeded";
    }

    private static CompletableFuture<String> authoritative(Minecraft mc, String expected, int damage) {
        return mc.getSingleplayerServer().submit(() -> {
            Player player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            if (!(player.containerMenu instanceof FilterMenu menu)) {
                return "server menu missing";
            }
            String raw = FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), 0);
            ItemStack match = new ItemStack(Items.DIAMOND_SWORD);
            match.set(DataComponents.DAMAGE, damage);
            ItemStack miss = new ItemStack(Items.DIAMOND_SWORD);
            miss.set(DataComponents.DAMAGE, damage + 1);
            boolean positive = FilterItemData.containsItemFull(menu.getOpenedStack(), match, player.level().registryAccess());
            boolean negative = FilterItemData.containsItemFull(menu.getOpenedStack(), miss, player.level().registryAccess());
            return expected.equals(raw) && positive && !negative ? "matched"
                    : "raw=" + raw + ",positive=" + positive + ",negative=" + negative;
        });
    }

    private static boolean serverMatched(Minecraft mc, String expected, int damage) {
        if (!serverCheck.isDone()) {
            return false;
        }
        String result = serverCheck.join();
        if ("matched".equals(result)) {
            serverRetries = 0;
            return true;
        }
        require(++serverRetries < 50, "server state timeout " + result);
        serverCheck = authoritative(mc, expected, damage);
        return false;
    }

    private static String createNode(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
        BlockPos pos = player.blockPosition().offset(2, 0, 0);
        player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        net.minecraft.world.level.block.entity.ChestBlockEntity chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity) player.level().getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        chest.setItem(1, new ItemStack(Items.DIRT, 4));
        chest.setChanged();
        LogisticsNodeEntity node = NodePlacementHelper.placeNode(player.level(), pos);
        if (node == null) {
            return "node placement failed";
        }
        node.setOwnerUUID(playerId);
        NetworkRegistry registry = NetworkRegistry.get((net.minecraft.server.level.ServerLevel) player.level());
        LogisticsNetwork network = registry.createNetwork("client-audit", playerId);
        node.setNetworkId(network.getId());
        node.setNetworkName(network.getName());
        registry.addNodeToNetwork(network.getId(), node.getUUID());
        node.getChannel(0).setFilterItem(0, player.getInventory().getItem(0).copy());
        registry.invalidateNetwork(network.getId());
        return "node=" + node.getId();
    }

    private static CompletableFuture<String> scanState(Minecraft mc) {
        return mc.getSingleplayerServer().submit(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            if (!(player.containerMenu instanceof FilterMenu menu) || !menu.isNodeFilter()) {
                return "node menu missing";
            }
            ItemStack filter = menu.getOpenedStack();
            boolean items = FilterItemData.containsItem(filter, new ItemStack(Items.COBBLESTONE), player.registryAccess())
                    && FilterItemData.containsItem(filter, new ItemStack(Items.DIRT), player.registryAccess());
            return "entries=" + FilterItemData.getEntryCount(filter) + ",items=" + items;
        });
    }

    private static CompletableFuture<String> nodeFilterState(Minecraft mc) {
        return mc.getSingleplayerServer().submit(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            if (!(player.containerMenu instanceof NodeMenu menu)) {
                return "node menu missing";
            }
            return menu.getNode().getChannel(0).getFilterItem(0).isEmpty() ? "empty" : "present";
        });
    }

    private static String prepareModels(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
        Direction direction = player.getDirection();
        BlockPos pos = player.blockPosition().relative(direction, 2);
        player.level().setBlockAndUpdate(pos, Registration.COMPUTER_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, direction));
        BlockPos itemPos = pos.relative(direction.getCounterClockWise());
        ItemEntity item = new ItemEntity(player.level(), itemPos.getX() + 0.5, itemPos.getY() + 0.8,
                itemPos.getZ() + 0.5, Registration.LOGISTICS_NODE_ITEM.get().getDefaultInstance());
        item.setNoGravity(true);
        player.level().addFreshEntity(item);
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, Registration.LOGISTICS_NODE_ITEM.get().getDefaultInstance());
        player.getInventory().setChanged();
        player.inventoryMenu.sendAllDataToRemote();
        return "prepared=" + pos.toShortString();
    }

    private static void clickScan(Minecraft mc, FilterScreen screen) throws Exception {
        Method x = FilterScreen.class.getDeclaredMethod("scanStorageButtonX");
        Method y = FilterScreen.class.getDeclaredMethod("clipboardButtonY");
        x.setAccessible(true);
        y.setAccessible(true);
        click(mc, (int) x.invoke(screen) + 6, (int) y.invoke(screen) + 6);
    }

    private static String overlayMessage(Minecraft mc) throws Exception {
        Field field = mc.gui.getClass().getDeclaredField("overlayMessageString");
        field.setAccessible(true);
        net.minecraft.network.chat.Component message =
                (net.minecraft.network.chat.Component) field.get(mc.gui);
        return message == null ? "" : message.getString();
    }

    private static void clickEntryDetail(Minecraft mc, FilterScreen screen) throws Exception {
        var slot = screen.getMenu().slots.get(0);
        keyDown(mc, GLFW.GLFW_KEY_LEFT_CONTROL, 0);
        click(mc, screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
        keyUp(mc, GLFW.GLFW_KEY_LEFT_CONTROL, 0);
        require(!ClientControls.modifier2Down(), "modifier remained held");
    }

    private static void clickConfigure(Minecraft mc, FilterScreen screen) throws Exception {
        int panelX = screen.getGuiLeft() + 4;
        int panelY = screen.getGuiTop() + 20;
        int contentX = panelX + 4;
        int nbtY = panelY + 20 + 22 + 22 + 22;
        int strictToggleX = contentX + 52;
        int x = strictToggleX + 14
                + mc.font.width(net.minecraft.network.chat.Component.translatable(
                        "gui.logisticsnetworks.filter.detail.nbt.strict")) + 12;
        click(mc, x, nbtY + 6);
    }

    private static void clickRawToggle(Minecraft mc, FilterScreen screen) throws Exception {
        click(mc, screen.getGuiLeft() + 10, screen.getGuiTop() + 46);
    }

    private static void focusRaw(Minecraft mc, FilterScreen screen) throws Exception {
        MultiLineEditBox box = box(screen);
        click(mc, box.getX() + 3, box.getY() + 3);
        require(box(screen).isFocused(), "raw editor refocus failed");
    }

    private static void replaceText(Minecraft mc, String text) throws Exception {
        key(mc, InputConstants.KEY_A, GLFW.GLFW_MOD_CONTROL);
        key(mc, InputConstants.KEY_BACKSPACE, 0);
        require(box(screen(mc)).getValue().isEmpty(), "scripted select-all failed");
        for (int codePoint : text.codePoints().toArray()) {
            character(mc, codePoint);
        }
        require(text.equals(box(screen(mc)).getValue()), "scripted typing mismatch");
    }

    private static void clickInsidePanelBorder(Minecraft mc, FilterScreen screen) throws Exception {
        click(mc, screen.getGuiLeft() + 5, screen.getGuiTop() + 42);
    }

    private static void clickOutsidePanel(Minecraft mc, FilterScreen screen) throws Exception {
        click(mc, screen.getGuiLeft(), screen.getGuiTop());
    }

    private static void click(Minecraft mc, double guiX, double guiY) throws Exception {
        move(mc, guiX, guiY);
        mouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void mouseButton(Minecraft mc, int mouseButton) throws Exception {
        long handle = mc.getWindow().handle();
        Method button = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
        button.setAccessible(true);
        MouseButtonInfo info = new MouseButtonInfo(mouseButton, 0);
        button.invoke(mc.mouseHandler, handle, info, GLFW.GLFW_PRESS);
        button.invoke(mc.mouseHandler, handle, info, GLFW.GLFW_RELEASE);
    }

    private static void move(Minecraft mc, double guiX, double guiY) throws Exception {
        long handle = mc.getWindow().handle();
        double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        Method move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(mc.mouseHandler, handle, x, y);
    }

    private static void moveToPlayerFilter(Minecraft mc, NodeScreen screen) throws Exception {
        var slot = screen.getMenu().slots.get(31);
        move(mc, screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
    }

    private static void key(Minecraft mc, int key, int modifiers) throws Exception {
        keyDown(mc, key, modifiers);
        keyUp(mc, key, modifiers);
    }

    private static void keyDown(Minecraft mc, int key, int modifiers) throws Exception {
        keyboard(mc).invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS,
                new KeyEvent(key, 0, modifiers));
    }

    private static void keyUp(Minecraft mc, int key, int modifiers) throws Exception {
        keyboard(mc).invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE,
                new KeyEvent(key, 0, modifiers));
    }

    private static Method keyboard(Minecraft mc) throws Exception {
        Method method = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        method.setAccessible(true);
        return method;
    }

    private static void character(Minecraft mc, int codePoint) throws Exception {
        Method method = KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, CharacterEvent.class);
        method.setAccessible(true);
        method.invoke(mc.keyboardHandler, mc.getWindow().handle(), new CharacterEvent(codePoint));
    }

    private static FilterScreen screen(Minecraft mc) {
        if (!(mc.screen instanceof FilterScreen screen)) {
            throw new IllegalStateException("filter screen missing");
        }
        return screen;
    }

    private static NodeScreen nodeScreen(Minecraft mc) {
        if (!(mc.screen instanceof NodeScreen screen)) {
            throw new IllegalStateException("node screen missing");
        }
        return screen;
    }

    private static MultiLineEditBox box(FilterScreen screen) throws Exception {
        return (MultiLineEditBox) field(screen, "detailNbtInputBox").get(screen);
    }

    private static int intField(FilterScreen screen, String name) throws Exception {
        return field(screen, name).getInt(screen);
    }

    private static boolean booleanField(FilterScreen screen, String name) throws Exception {
        return field(screen, name).getBoolean(screen);
    }

    private static Field field(Object screen, String name) throws Exception {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String localRaw(FilterScreen screen) {
        return FilterItemData.getEntryNbtRaw(screen.getMenu().getOpenedStack(), 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void finish(Minecraft mc, Path screenshot) throws Exception {
        stage = Stage.DONE;
        String text = "result=PASS\nwindowActive=" + mc.isWindowActive() + "\nmenuId="
                + filterMenuId + "\nraw=" + finalRaw
                + "\neditorNameTyped=" + editorNameTyped
                + "\nmodalKeyPreserved=" + modalKeyPreserved
                + "\nnodeKeyOpenedFilter=" + nodeKeyOpenedFilter
                + "\nnormalKeyOpenedFilter=" + normalKeyOpenedFilter
                + "\nfirstScanMessage=" + firstScanMessage
                + "\nsecondScanMessage=" + secondScanMessage
                + "\nscreenshot=" + screenshot.toAbsolutePath() + "\nscreenshotMessage=" + screenshotResult + "\n";
        text += "modelScreenshot=" + modelScreenshot.toAbsolutePath() + "\n";
        Files.writeString(mc.gameDirectory.toPath().resolve("parity-client.txt"), text);
        ClientControls.SECONDARY_INTERACTION.setKey(ClientControls.SECONDARY_INTERACTION.getDefaultKey());
        ClientControls.PRIMARY_INTERACTION.setKey(ClientControls.PRIMARY_INTERACTION.getDefaultKey());
        KeyMapping.resetMapping();
        mc.disconnectWithSavingScreen();
        mc.stop();
    }

    private static void fail(Minecraft mc, Throwable throwable) {
        if (failure != null) {
            return;
        }
        failure = throwable.toString();
        stage = Stage.DONE;
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("parity-client.txt"),
                    "result=FAIL\nfailure=" + failure + "\n");
        } catch (Exception ignored) {
        }
        throwable.printStackTrace();
        mc.disconnectWithSavingScreen();
        mc.stop();
    }

    private enum Stage {
        BOOT, WORLD, SEEDED, MENU, DETAIL, NBT_TABLE, NBT_RAW, TYPE_OUTSIDE, CHECK_OUTSIDE,
        CHECK_RETURN, CHECK_NUMPAD, CHECK_CLOSE, REOPEN_DETAIL, REOPEN_TABLE, REOPEN_RAW, CAPTURE,
        NODE_FIXTURE, NODE_MENU, NODE_NETWORKS, NODE_EDITOR, EDITOR_CAPTURE, NODE_EDITOR_KEY,
        NODE_EDITOR_ASSERT_CAPTURE, NODE_EDITOR_ASSERT,
        NODE_EDITOR_SERVER, NODE_CHANNEL_MENU, NODE_FILTER_KEY, NODE_FILTER_ASSERT, SCAN_FIRST, SCAN_SECOND, NODE_RETURN,
        NODE_REMOVE, NODE_REMOVE_ASSERT, NODE_NORMAL_KEY, NODE_NORMAL_ASSERT, MODEL_SETUP, MODEL_SETTLE, MODEL_CAPTURE,
        DONE
    }
}
