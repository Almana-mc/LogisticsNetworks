package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.Set;

public class WrenchItem extends Item {

    private static final String KEY_ROOT = "ln_wrench";
    private static final String KEY_MODE = "mode";
    private static final String KEY_CLIPBOARD = "clipboard";
    private static final String KEY_MASS_SELECTIONS = "mass_selections";
    private static final String KEY_SELECTION_DIMENSION = "dimension";
    private static final String KEY_SELECTION_POS = "pos";
    private static final String KEY_AE2_LINK = "ae2_link";
    private static final int MAX_MASS_SELECTIONS = 2048;

    public record MassSelectionTarget(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public enum Mode {
        WRENCH("wrench"),
        COPY_PASTE("copy_paste"),
        MASS_PLACEMENT("mass_placement");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public Mode next() {
            return switch (this) {
                case WRENCH -> COPY_PASTE;
                case COPY_PASTE -> MASS_PLACEMENT;
                case MASS_PLACEMENT -> WRENCH;
            };
        }

        public Mode previous() {
            return switch (this) {
                case WRENCH -> MASS_PLACEMENT;
                case COPY_PASTE -> WRENCH;
                case MASS_PLACEMENT -> COPY_PASTE;
            };
        }

        public static Mode fromId(String id) {
            if (id == null) {
                return WRENCH;
            }
            for (Mode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return WRENCH;
        }
    }

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return switch (getMode(context.getItemInHand())) {
            case WRENCH -> useOnWrenchMode(context);
            case COPY_PASTE -> useOnCopyPasteMode(context);
            case MASS_PLACEMENT -> useOnMassPlacementMode(context);
        };
    }

    private InteractionResult useOnWrenchMode(UseOnContext context) {
        return useOnShared(context);
    }

    private InteractionResult useOnCopyPasteMode(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        LogisticsNodeEntity node = findNodeAt(level, context.getClickedPos());
        if (node == null) {
            return InteractionResult.SUCCESS;
        }

        if (!node.isOwnedBy(player)) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.not_owner"), true);
            return InteractionResult.FAIL;
        }

        ItemStack wrenchStack = context.getItemInHand();
        return isSecondaryUse(player)
                ? pasteToNode(node, player, wrenchStack)
                : copyFromNode(node, player, wrenchStack);
    }

    private InteractionResult useOnMassPlacementMode(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        ItemStack wrenchStack = context.getItemInHand();
        MassSelectionTarget target = new MassSelectionTarget(player.level().dimension(), context.getClickedPos());

        if (toggleMassSelection(wrenchStack, target)) {
            int selectedCount = getMassSelectionCount(wrenchStack, player.level().dimension());
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.mass_placement.deselected", selectedCount), true);
            return InteractionResult.CONSUME;
        }

        NodePlacementHelper.ValidationResult validation = NodePlacementHelper.validatePlacement(level, target.pos(), player.isCreative());
        switch (validation) {
            case BLACKLISTED -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.block_blacklisted"), true);
            case NO_STORAGE_CAPABILITY -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.no_storage_capability"), true);
            case NODE_ALREADY_EXISTS -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.node_already_exists"), true);
            case AIR -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.mass_placement.invalid_air"), true);
            case OK -> {
                if (addMassSelection(wrenchStack, target)) {
                    int selectedCount = getMassSelectionCount(wrenchStack, player.level().dimension());
                    sendPlayerMessage(player,
                            Component.translatable("message.logisticsnetworks.mass_placement.selected", selectedCount),
                            true);
                } else {
                    sendPlayerMessage(player,
                            Component.translatable("message.logisticsnetworks.mass_placement.selection_limit"), true);
                }
            }
        }

        return InteractionResult.CONSUME;
    }

    public static boolean handleConnectedSelection(ServerPlayer player, InteractionHand hand, BlockPos origin) {
        if (player == null || hand == null || origin == null) {
            return false;
        }

        ItemStack wrenchStack = player.getItemInHand(hand);
        if (!(wrenchStack.getItem() instanceof WrenchItem) || getMode(wrenchStack) != Mode.MASS_PLACEMENT) {
            return false;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        MassSelectionTarget originTarget = new MassSelectionTarget(dimension, origin);
        if (hasMassSelection(wrenchStack, originTarget)) {
            int removed = removeConnectedSelections(player, wrenchStack, origin);
            int selectedCount = getMassSelectionCount(wrenchStack, dimension);
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.mass_placement.connected_deselected",
                            removed, selectedCount),
                    true);
            return true;
        }

        NodePlacementHelper.ValidationResult validation = NodePlacementHelper.validatePlacement(player.level(), origin, player.isCreative());
        switch (validation) {
            case BLACKLISTED -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.block_blacklisted"), true);
            case NO_STORAGE_CAPABILITY -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.no_storage_capability"), true);
            case NODE_ALREADY_EXISTS -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.node_already_exists"), true);
            case AIR -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.mass_placement.invalid_air"), true);
            case OK -> {
                int added = addConnectedSelections(player, wrenchStack, origin);
                int selectedCount = getMassSelectionCount(wrenchStack, player.level().dimension());
                sendPlayerMessage(player,
                        Component.translatable("message.logisticsnetworks.mass_placement.connected_selected",
                                added, selectedCount),
                        true);
            }
        }

        return true;
    }

    public static boolean handleConnectedPaste(ServerPlayer player, InteractionHand hand, BlockPos origin) {
        if (player == null || hand == null || origin == null) {
            return false;
        }

        ItemStack wrenchStack = player.getItemInHand(hand);
        if (!(wrenchStack.getItem() instanceof WrenchItem) || getMode(wrenchStack) != Mode.COPY_PASTE) {
            return false;
        }

        NodeClipboardConfig clipboard = getClipboard(wrenchStack, player.registryAccess());
        if (clipboard == null) {
            String key = hasClipboardPayload(wrenchStack)
                    ? "message.logisticsnetworks.clipboard.invalid"
                    : "message.logisticsnetworks.clipboard.empty";
            sendPlayerMessage(player, Component.translatable(key), true);
            return true;
        }
        if (clipboard.isEffectivelyEmpty()) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.empty"), true);
            return true;
        }

        Level level = player.level();
        BlockState originState = level.getBlockState(origin);
        if (originState.isAir()) {
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.connected.none"), true);
            return true;
        }

        int scanned = 0;
        int maxScan = 16384;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        List<LogisticsNodeEntity> targets = new ArrayList<>();

        while (!queue.isEmpty() && scanned < maxScan) {
            BlockPos current = queue.pollFirst();
            if (!visited.add(current)) {
                continue;
            }

            scanned++;
            BlockState state = level.getBlockState(current);
            if (state.getBlock() != originState.getBlock()) {
                continue;
            }

            LogisticsNodeEntity node = findNodeAt(level, current);
            if (node != null && node.isOwnedBy(player)) {
                targets.add(node);
            }

            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).getBlock() == originState.getBlock()) {
                    queue.addLast(next);
                }
            }
        }

        if (targets.isEmpty()) {
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.connected.none"), true);
            return true;
        }

        int pasted = 0;
        boolean missingItems = false;
        boolean inventoryFull = false;
        boolean incompatibleOnly = false;
        GlobalPos ae2Link = getAE2LinkPos(wrenchStack);

        for (LogisticsNodeEntity node : targets) {
            NodeClipboardConfig.PasteResult result = clipboard.applyToNode(player, node, wrenchStack, ae2Link);
            switch (result) {
                case SUCCESS -> {
                    pasted++;
                    markNodeNetworkDirty(node);
                }
                case MISSING_ITEMS -> {
                    missingItems = true;
                    break;
                }
                case INVENTORY_FULL -> {
                    inventoryFull = true;
                    break;
                }
                case INCOMPATIBLE_TARGET -> incompatibleOnly = true;
                case CLIPBOARD_INVALID -> {
                    sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.invalid"),
                            true);
                    return true;
                }
            }
        }

        if (pasted > 0) {
            if (missingItems) {
                sendPlayerMessage(player,
                        Component.translatable("message.logisticsnetworks.clipboard.paste.connected.partial_missing",
                                pasted),
                        true);
            } else if (inventoryFull) {
                sendPlayerMessage(player,
                        Component.translatable("message.logisticsnetworks.clipboard.paste.connected.partial_no_space",
                                pasted),
                        true);
            } else {
                sendPlayerMessage(player,
                        Component.translatable("message.logisticsnetworks.clipboard.paste.connected.success", pasted),
                        true);
            }
            return true;
        }

        if (missingItems) {
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.missing_items"), true);
            return true;
        }
        if (inventoryFull) {
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.no_space"), true);
            return true;
        }
        if (incompatibleOnly) {
            sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.incompatible"), true);
            return true;
        }

        sendPlayerMessage(player,
                Component.translatable("message.logisticsnetworks.clipboard.paste.connected.none"), true);
        return true;
    }

    private static int addConnectedSelections(ServerPlayer player, ItemStack wrenchStack, BlockPos origin) {
        Level level = player.level();
        BlockState originState = level.getBlockState(origin);
        if (originState.isAir()) {
            return 0;
        }

        int remainingCapacity = MAX_MASS_SELECTIONS - getMassSelectionCount(wrenchStack, player.level().dimension());
        if (remainingCapacity <= 0) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.mass_placement.selection_limit"),
                    true);
            return 0;
        }

        int added = 0;
        int scanned = 0;
        int maxScan = 16384;

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty() && scanned < maxScan && remainingCapacity > 0) {
            BlockPos current = queue.pollFirst();
            if (!visited.add(current)) {
                continue;
            }

            scanned++;
            BlockState state = level.getBlockState(current);
            if (state.getBlock() != originState.getBlock()) {
                continue;
            }

            if (NodePlacementHelper.validatePlacement(level, current, player.isCreative()) == NodePlacementHelper.ValidationResult.OK) {
                MassSelectionTarget target = new MassSelectionTarget(player.level().dimension(), current);
                if (addMassSelection(wrenchStack, target)) {
                    added++;
                    remainingCapacity--;
                }
            }

            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).getBlock() == originState.getBlock()) {
                    queue.addLast(next);
                }
            }
        }

        if (remainingCapacity <= 0) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.mass_placement.selection_limit"),
                    true);
        }

        return added;
    }

    private static int removeConnectedSelections(ServerPlayer player, ItemStack wrenchStack, BlockPos origin) {
        Level level = player.level();
        BlockState originState = level.getBlockState(origin);
        if (originState.isAir()) {
            return 0;
        }

        int scanned = 0;
        int maxScan = 16384;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty() && scanned < maxScan) {
            BlockPos current = queue.pollFirst();
            if (!visited.add(current)) {
                continue;
            }

            scanned++;
            BlockState state = level.getBlockState(current);
            if (state.getBlock() != originState.getBlock()) {
                continue;
            }

            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).getBlock() == originState.getBlock()) {
                    queue.addLast(next);
                }
            }
        }

        List<MassSelectionTarget> toRemove = new ArrayList<>();
        for (MassSelectionTarget existing : getMassSelections(wrenchStack, player.level().dimension())) {
            if (visited.contains(existing.pos())) {
                toRemove.add(existing);
            }
        }

        removeMassSelections(wrenchStack, toRemove);
        return toRemove.size();
    }

    private static boolean hasMassSelection(ItemStack stack, MassSelectionTarget target) {
        if (target == null) {
            return false;
        }
        for (MassSelectionTarget existing : getMassSelections(stack, target.dimension())) {
            if (existing.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private InteractionResult useOnShared(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        LogisticsNodeEntity node = findNodeAt(level, clickedPos);
        if (node == null) {
            if (player.isShiftKeyDown() && AE2Compat.isLoaded() && AE2Compat.isGridHost(level, clickedPos)) {
                return toggleAE2Link(context.getItemInHand(), player, level, clickedPos);
            }
            return InteractionResult.SUCCESS;
        }

        if (!node.isOwnedBy(player)) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.not_owner"), true);
            return InteractionResult.FAIL;
        }

        // Claim unowned nodes on first interaction
        if (node.getOwnerUUID() == null) {
            node.setOwnerUUID(player.getUUID());
        }

        if (player.isShiftKeyDown()) {
            return removeNode(level, node, player);
        }
        return openNodeGui(node, player);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return switch (getMode(stack)) {
            case WRENCH -> useAirWrenchMode(level, player, hand, stack);
            case COPY_PASTE -> useAirCopyPasteMode(level, player, hand, stack);
            case MASS_PLACEMENT -> useAirMassPlacementMode(level, player, hand, stack);
        };
    }

    private InteractionResult useAirWrenchMode(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        return InteractionResult.PASS;
    }

    private InteractionResult useAirCopyPasteMode(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (isSecondaryUse(player)) {
            sendClipboardPreview(serverPlayer, stack);
        } else {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ClipboardMenu(id, inventory, hand),
                    Component.translatable("gui.logisticsnetworks.clipboard")),
                    buf -> buf.writeVarInt(hand.ordinal()));
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult useAirMassPlacementMode(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new MassPlacementMenu(id, inventory, hand),
                Component.translatable("gui.logisticsnetworks.mass_placement")),
                buf -> buf.writeVarInt(hand.ordinal()));

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return switch (getMode(stack)) {
            case WRENCH -> onLeftClickEntityWrenchMode(stack, player, entity);
            case COPY_PASTE -> onLeftClickEntityCopyPasteMode(stack, player, entity);
            case MASS_PLACEMENT -> onLeftClickEntityMassPlacementMode(stack, player, entity);
        };
    }

    private boolean onLeftClickEntityWrenchMode(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    private boolean onLeftClickEntityCopyPasteMode(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    private boolean onLeftClickEntityMassPlacementMode(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    public static Mode getMode(ItemStack stack) {
        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_MODE)) {
            return Mode.WRENCH;
        }
        return Mode.fromId(root.getStringOr(KEY_MODE, Mode.WRENCH.id()));
    }

    public static void setMode(ItemStack stack, Mode mode) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem)) {
            return;
        }

        Mode resolved = mode == null ? Mode.WRENCH : mode;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (resolved == Mode.WRENCH) {
                root.remove(KEY_MODE);
            } else {
                root.putString(KEY_MODE, resolved.id());
            }
            writeRoot(customTag, root);
        });
    }

    public static Mode cycleMode(ItemStack stack, boolean forward) {
        Mode nextMode = forward ? getMode(stack).next() : getMode(stack).previous();
        setMode(stack, nextMode);
        return nextMode;
    }

    public static Component getModeDisplayName(Mode mode) {
        Mode resolved = mode == null ? Mode.WRENCH : mode;
        ChatFormatting color = switch (resolved) {
            case WRENCH -> ChatFormatting.BLUE;
            case COPY_PASTE -> ChatFormatting.GREEN;
            case MASS_PLACEMENT -> ChatFormatting.GOLD;
        };
        return Component.translatable("tooltip.logisticsnetworks.wrench.mode." + resolved.id()).withStyle(color);
    }

    public static Component getModeChangedMessage(Mode mode) {
        return Component.translatable("message.logisticsnetworks.wrench_mode", getModeDisplayName(mode));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.wrench.mode", getModeDisplayName(getMode(stack))));
        GlobalPos ae2Link = getAE2LinkPos(stack);
        if (ae2Link != null) {
            var window = net.minecraft.client.Minecraft.getInstance().getWindow();
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                    || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) {
                BlockPos p = ae2Link.pos();
                String dim = ae2Link.dimension().identifier().toString();
                tooltip.accept(Component.translatable("tooltip.logisticsnetworks.wrench.ae2_linked_detail",
                        p.getX(), p.getY(), p.getZ(), dim).withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.accept(Component.translatable("tooltip.logisticsnetworks.wrench.ae2_linked")
                        .withStyle(ChatFormatting.GREEN));
            }
        } else if (AE2Compat.isLoaded()) {
            tooltip.accept(Component.translatable("tooltip.logisticsnetworks.wrench.ae2_unlinked")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Nullable
    private static LogisticsNodeEntity findNodeAt(Level level, BlockPos pos) {
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.5));
        for (LogisticsNodeEntity node : nodes) {
            if (node.getAttachedPos().equals(pos) && node.isActive()) {
                return node;
            }
        }
        return null;
    }

    private InteractionResult removeNode(Level level, LogisticsNodeEntity node, Player player) {
        if (level instanceof ServerLevel serverLevel && node.getNetworkId() != null) {
            NetworkRegistry.get(serverLevel).removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
        }

        node.dropFilters();
        node.dropUpgrades();
        if (level instanceof ServerLevel serverLevel) {
            node.spawnAtLocation(serverLevel, Registration.logisticsNodeItem());
        }
        node.discard();

        level.playSound(null, node.blockPosition(), SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.node_removed"), true);

        return InteractionResult.CONSUME;
    }

    private InteractionResult openNodeGui(LogisticsNodeEntity node, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.logisticsnetworks.node_config");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player p) {
                    return new NodeMenu(containerId, playerInv, node);
                }
            }, buf -> Util.writeNodeSyncData(buf, node, player.registryAccess()));

            if (serverPlayer.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult copyFromNode(LogisticsNodeEntity node, Player player, ItemStack wrenchStack) {
        NodeClipboardConfig clipboard = NodeClipboardConfig.fromNode(node);
        setClipboard(wrenchStack, clipboard, player.registryAccess());
        sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.copied"), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pasteToNode(LogisticsNodeEntity node, Player player, ItemStack wrenchStack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        NodeClipboardConfig clipboard = getClipboard(wrenchStack, serverPlayer.registryAccess());
        if (clipboard == null) {
            String key = hasClipboardPayload(wrenchStack)
                    ? "message.logisticsnetworks.clipboard.invalid"
                    : "message.logisticsnetworks.clipboard.empty";
            sendPlayerMessage(player, Component.translatable(key), true);
            return InteractionResult.CONSUME;
        }
        if (clipboard.isEffectivelyEmpty()) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.empty"), true);
            return InteractionResult.CONSUME;
        }

        GlobalPos ae2Link = getAE2LinkPos(wrenchStack);
        NodeClipboardConfig.PasteResult result = clipboard.applyToNode(serverPlayer, node, wrenchStack, ae2Link);
        switch (result) {
            case SUCCESS -> {
                markNodeNetworkDirty(node);
                sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.paste.success"),
                        true);
            }
            case MISSING_ITEMS -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.missing_items"), true);
            case INVENTORY_FULL -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.no_space"), true);
            case INCOMPATIBLE_TARGET -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.incompatible"), true);
            case CLIPBOARD_INVALID -> sendPlayerMessage(player,
                    Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
        }

        return InteractionResult.CONSUME;
    }

    private static void markNodeNetworkDirty(LogisticsNodeEntity node) {
        if (node.getNetworkId() != null && node.level() instanceof ServerLevel serverLevel) {
            NetworkRegistry.get(serverLevel).markNetworkDirty(node.getNetworkId());
        }
    }

    private static CompoundTag getRootTag(ItemStack stack) {
        return getRootTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    private static boolean hasClipboardPayload(ItemStack stack) {
        return getRootTag(stack).contains(KEY_CLIPBOARD);
    }

    private static CompoundTag getRootTag(CompoundTag customTag) {
        if (customTag.contains(KEY_ROOT)) {
            return customTag.getCompound(KEY_ROOT).map(CompoundTag::copy).orElseGet(CompoundTag::new);
        }
        return new CompoundTag();
    }

    private static void writeRoot(CompoundTag customTag, CompoundTag root) {
        if (root.isEmpty()) {
            customTag.remove(KEY_ROOT);
        } else {
            customTag.put(KEY_ROOT, root);
        }
    }

    @Nullable
    public static NodeClipboardConfig getClipboard(ItemStack stack, HolderLookup.Provider provider) {
        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_CLIPBOARD)) {
            return null;
        }
        return root.getCompound(KEY_CLIPBOARD)
                .map(tag -> NodeClipboardConfig.load(tag, provider))
                .orElse(null);
    }

    public static void setClipboard(ItemStack stack, NodeClipboardConfig clipboard, HolderLookup.Provider provider) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (clipboard == null) {
                root.remove(KEY_CLIPBOARD);
            } else {
                root.put(KEY_CLIPBOARD, clipboard.save(provider));
            }
            writeRoot(customTag, root);
        });
    }

    public static void clearClipboard(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            root.remove(KEY_CLIPBOARD);
            writeRoot(customTag, root);
        });
    }

    private InteractionResult toggleAE2Link(ItemStack wrenchStack, Player player, Level level, BlockPos clickedPos) {
        GlobalPos current = getAE2LinkPos(wrenchStack);
        if (current != null && current.pos().equals(clickedPos) && current.dimension().equals(level.dimension())) {
            clearAE2Link(wrenchStack);
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.ae2.unlinked"), true);
        } else {
            setAE2Link(wrenchStack, level.dimension(), clickedPos);
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.ae2.linked",
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
        }
        return InteractionResult.CONSUME;
    }

    public static void setAE2Link(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        if (stack.isEmpty()) return;
        GlobalPos globalPos = GlobalPos.of(dimension, pos);
        Tag encoded = GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, globalPos)
                .result().orElse(null);
        if (encoded == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            root.put(KEY_AE2_LINK, encoded);
            writeRoot(customTag, root);
        });
    }

    public static void clearAE2Link(ItemStack stack) {
        if (stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            root.remove(KEY_AE2_LINK);
            writeRoot(customTag, root);
        });
    }

    public static boolean hasAE2Link(ItemStack stack) {
        return getRootTag(stack).contains(KEY_AE2_LINK);
    }

    @Nullable
    public static GlobalPos getAE2LinkPos(ItemStack stack) {
        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_AE2_LINK)) return null;
        return GlobalPos.CODEC.decode(net.minecraft.nbt.NbtOps.INSTANCE, root.get(KEY_AE2_LINK))
                .result().map(com.mojang.datafixers.util.Pair::getFirst).orElse(null);
    }

    public static List<MassSelectionTarget> getMassSelections(ItemStack stack) {
        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_MASS_SELECTIONS)) {
            return List.of();
        }

        ListTag list = root.getListOrEmpty(KEY_MASS_SELECTIONS);
        List<MassSelectionTarget> targets = new ArrayList<>(list.size());
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }

            if (!entry.contains(KEY_SELECTION_DIMENSION)) {
                continue;
            }

            Identifier dimensionId = Identifier.tryParse(entry.getStringOr(KEY_SELECTION_DIMENSION, ""));
            if (dimensionId == null) {
                continue;
            }

            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            BlockPos pos = BlockPos.of(entry.getLongOr(KEY_SELECTION_POS, 0L));
            targets.add(new MassSelectionTarget(dimension, pos));
        }
        return targets;
    }

    public static List<MassSelectionTarget> getMassSelections(ItemStack stack, ResourceKey<Level> dimension) {
        if (dimension == null) {
            return List.of();
        }
        return getMassSelections(stack).stream()
                .filter(target -> target.dimension().equals(dimension))
                .toList();
    }

    public static int getMassSelectionCount(ItemStack stack, ResourceKey<Level> dimension) {
        return getMassSelections(stack, dimension).size();
    }

    public static boolean toggleMassSelection(ItemStack stack, MassSelectionTarget target) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem) || target == null) {
            return false;
        }

        boolean[] removed = { false };
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            ListTag list = root.getListOrEmpty(KEY_MASS_SELECTIONS);

            ListTag updated = new ListTag();
            for (Tag tag : list) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                MassSelectionTarget existing = readSelection(entry);
                if (existing != null && existing.equals(target)) {
                    removed[0] = true;
                    continue;
                }
                updated.add(entry.copy());
            }

            if (updated.isEmpty()) {
                root.remove(KEY_MASS_SELECTIONS);
            } else {
                root.put(KEY_MASS_SELECTIONS, updated);
            }
            writeRoot(customTag, root);
        });
        return removed[0];
    }

    public static boolean addMassSelection(ItemStack stack, MassSelectionTarget target) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem) || target == null) {
            return false;
        }

        boolean[] added = { false };
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            ListTag list = root.getListOrEmpty(KEY_MASS_SELECTIONS);

            for (Tag tag : list) {
                if (tag instanceof CompoundTag entry) {
                    MassSelectionTarget existing = readSelection(entry);
                    if (target.equals(existing)) {
                        writeRoot(customTag, root);
                        return;
                    }
                }
            }

            if (list.size() >= MAX_MASS_SELECTIONS) {
                writeRoot(customTag, root);
                return;
            }

            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_SELECTION_DIMENSION, target.dimension().identifier().toString());
            entry.putLong(KEY_SELECTION_POS, target.pos().asLong());
            list.add(entry);
            root.put(KEY_MASS_SELECTIONS, list);
            writeRoot(customTag, root);
            added[0] = true;
        });
        return added[0];
    }

    public static void removeMassSelections(ItemStack stack, List<MassSelectionTarget> targetsToRemove) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem)
                || targetsToRemove == null || targetsToRemove.isEmpty()) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            ListTag list = root.getListOrEmpty(KEY_MASS_SELECTIONS);
            ListTag updated = new ListTag();

            for (Tag tag : list) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                MassSelectionTarget existing = readSelection(entry);
                if (existing == null || targetsToRemove.contains(existing)) {
                    continue;
                }
                updated.add(entry.copy());
            }

            if (updated.isEmpty()) {
                root.remove(KEY_MASS_SELECTIONS);
            } else {
                root.put(KEY_MASS_SELECTIONS, updated);
            }
            writeRoot(customTag, root);
        });
    }

    public static void clearMassSelections(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WrenchItem)) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            root.remove(KEY_MASS_SELECTIONS);
            writeRoot(customTag, root);
        });
    }

    @Nullable
    private static MassSelectionTarget readSelection(CompoundTag entry) {
        if (!entry.contains(KEY_SELECTION_DIMENSION)) {
            return null;
        }

        Identifier dimensionId = Identifier.tryParse(entry.getStringOr(KEY_SELECTION_DIMENSION, ""));
        if (dimensionId == null) {
            return null;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos pos = BlockPos.of(entry.getLongOr(KEY_SELECTION_POS, 0L));
        return new MassSelectionTarget(dimension, pos);
    }

    private void sendClipboardPreview(ServerPlayer player, ItemStack wrenchStack) {
        NodeClipboardConfig clipboard = getClipboard(wrenchStack, player.registryAccess());
        if (clipboard == null) {
            String key = hasClipboardPayload(wrenchStack)
                    ? "message.logisticsnetworks.clipboard.invalid"
                    : "message.logisticsnetworks.clipboard.empty";
            sendPlayerMessage(player, Component.translatable(key), true);
            return;
        }

        if (!clipboard.isStructurallyValid()) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
            return;
        }
        if (clipboard.isEffectivelyEmpty()) {
            sendPlayerMessage(player, Component.translatable("message.logisticsnetworks.clipboard.empty"), true);
            return;
        }

        int enabledChannels = clipboard.getEnabledChannelCount();
        int filters = clipboard.getTotalFilterCount();
        int upgrades = clipboard.getTotalUpgradeCount();
        int requiredStacks = clipboard.getRequiredItemsPreview().size();

        player.sendSystemMessage(Component.translatable("message.logisticsnetworks.clipboard.preview.header"));
        player.sendSystemMessage(Component.translatable("message.logisticsnetworks.clipboard.preview.summary",
                enabledChannels, filters, upgrades, requiredStacks));

        int shown = 0;
        for (int channel = 0; channel < clipboard.getChannelCount() && shown < 3; channel++) {
            int channelFilters = clipboard.getFilterCountInChannel(channel);
            if (!clipboard.isChannelEnabled(channel) && channelFilters == 0) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("message.logisticsnetworks.clipboard.preview.channel",
                    channel,
                    Component.translatable("gui.logisticsnetworks.channel_mode."
                            + clipboard.getChannelMode(channel).name().toLowerCase(Locale.ROOT)).getString(),
                    Component.translatable("gui.logisticsnetworks.channel_type."
                            + clipboard.getChannelType(channel).name().toLowerCase(Locale.ROOT)).getString(),
                    channelFilters));
            shown++;
        }
    }

    public static void sendPlayerMessage(Player player, Component message, boolean actionBar) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, actionBar);
            return;
        }
        player.sendSystemMessage(message);
    }

    private static boolean isSecondaryUse(Player player) {
        return player.isSecondaryUseActive() || player.isShiftKeyDown() || player.isCrouching();
    }

    private static class Util {
        static void writeNodeSyncData(FriendlyByteBuf buf, LogisticsNodeEntity node,
                HolderLookup.Provider provider) {
            buf.writeVarInt(node.getId());
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                buf.writeNbt(node.getChannel(i).save(provider));
            }
            for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
                CompoundTag entry = new CompoundTag();
                entry.store("Item", ItemStack.OPTIONAL_CODEC, node.getUpgradeItem(i));
                buf.writeNbt(entry);
            }
        }
    }
}
