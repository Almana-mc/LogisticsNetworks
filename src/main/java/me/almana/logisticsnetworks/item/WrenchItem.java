package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WrenchItem extends Item {

    private static final String KEY_ROOT = "ln_wrench";
    private static final String KEY_MODE = "mode";
    private static final String KEY_CLIPBOARD = "clipboard";

    public enum Mode {
        WRENCH("wrench"),
        COPY_PASTE("copy_paste");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public Mode next() {
            return this == WRENCH ? COPY_PASTE : WRENCH;
        }

        public Mode previous() {
            return this == WRENCH ? COPY_PASTE : WRENCH;
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
        };
    }

    private InteractionResult useOnWrenchMode(UseOnContext context) {
        return useOnShared(context);
    }

    private InteractionResult useOnCopyPasteMode(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
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

        ItemStack wrenchStack = context.getItemInHand();
        return isSecondaryUse(player)
                ? pasteToNode(node, player, wrenchStack)
                : copyFromNode(node, player, wrenchStack);
    }

    private InteractionResult useOnShared(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        LogisticsNodeEntity node = findNodeAt(level, clickedPos);
        if (node == null) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            return removeNode(level, node, player);
        }
        return openNodeGui(node, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return switch (getMode(stack)) {
            case WRENCH -> useAirWrenchMode(level, player, hand, stack);
            case COPY_PASTE -> useAirCopyPasteMode(level, player, hand, stack);
        };
    }

    private InteractionResultHolder<ItemStack> useAirWrenchMode(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        return InteractionResultHolder.pass(stack);
    }

    private InteractionResultHolder<ItemStack> useAirCopyPasteMode(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (isSecondaryUse(player)) {
            sendClipboardPreview(serverPlayer, stack);
        } else {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ClipboardMenu(id, inventory, hand),
                    Component.translatable("gui.logisticsnetworks.clipboard")),
                    buf -> buf.writeVarInt(hand.ordinal()));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return switch (getMode(stack)) {
            case WRENCH -> onLeftClickEntityWrenchMode(stack, player, entity);
            case COPY_PASTE -> onLeftClickEntityCopyPasteMode(stack, player, entity);
        };
    }

    private boolean onLeftClickEntityWrenchMode(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    private boolean onLeftClickEntityCopyPasteMode(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    public static Mode getMode(ItemStack stack) {
        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_MODE, Tag.TAG_STRING)) {
            return Mode.WRENCH;
        }
        return Mode.fromId(root.getString(KEY_MODE));
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
        ChatFormatting color = resolved == Mode.WRENCH ? ChatFormatting.BLUE : ChatFormatting.GREEN;
        return Component.translatable("tooltip.logisticsnetworks.wrench.mode." + resolved.id()).withStyle(color);
    }

    public static Component getModeChangedMessage(Mode mode) {
        return Component.translatable("message.logisticsnetworks.wrench_mode", getModeDisplayName(mode));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.wrench.mode", getModeDisplayName(getMode(stack))));
    }

    @Nullable
    private LogisticsNodeEntity findNodeAt(Level level, BlockPos pos) {
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
        node.spawnAtLocation(Registration.LOGISTICS_NODE_ITEM.get());
        node.discard();

        level.playSound(null, node.blockPosition(), SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.logisticsnetworks.node_removed"), true);

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
        player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.copied"), true);
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
            player.displayClientMessage(Component.translatable(key), true);
            return InteractionResult.CONSUME;
        }
        if (clipboard.isEffectivelyEmpty()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.empty"), true);
            return InteractionResult.CONSUME;
        }

        NodeClipboardConfig.PasteResult result = clipboard.applyToNode(serverPlayer, node, wrenchStack);
        switch (result) {
            case SUCCESS -> {
                markNodeNetworkDirty(node);
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.paste.success"),
                        true);
            }
            case MISSING_ITEMS -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.missing_items"), true);
            case INVENTORY_FULL -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.no_space"), true);
            case INCOMPATIBLE_TARGET -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.incompatible"), true);
            case CLIPBOARD_INVALID -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
        }

        return InteractionResult.CONSUME;
    }

    private void markNodeNetworkDirty(LogisticsNodeEntity node) {
        if (node.getNetworkId() != null && node.level() instanceof ServerLevel serverLevel) {
            NetworkRegistry.get(serverLevel).markNetworkDirty(node.getNetworkId());
        }
    }

    private static CompoundTag getRootTag(ItemStack stack) {
        return getRootTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    private static boolean hasClipboardPayload(ItemStack stack) {
        return getRootTag(stack).contains(KEY_CLIPBOARD, Tag.TAG_COMPOUND);
    }

    private static CompoundTag getRootTag(CompoundTag customTag) {
        if (customTag.contains(KEY_ROOT, Tag.TAG_COMPOUND)) {
            return customTag.getCompound(KEY_ROOT).copy();
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
        if (!root.contains(KEY_CLIPBOARD, Tag.TAG_COMPOUND)) {
            return null;
        }
        return NodeClipboardConfig.load(root.getCompound(KEY_CLIPBOARD), provider);
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

    private void sendClipboardPreview(ServerPlayer player, ItemStack wrenchStack) {
        NodeClipboardConfig clipboard = getClipboard(wrenchStack, player.registryAccess());
        if (clipboard == null) {
            String key = hasClipboardPayload(wrenchStack)
                    ? "message.logisticsnetworks.clipboard.invalid"
                    : "message.logisticsnetworks.clipboard.empty";
            player.displayClientMessage(Component.translatable(key), true);
            return;
        }

        if (!clipboard.isStructurallyValid()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
            return;
        }
        if (clipboard.isEffectivelyEmpty()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.empty"), true);
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
                    clipboard.getChannelMode(channel).name(),
                    clipboard.getChannelType(channel).name(),
                    channelFilters));
            shown++;
        }
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
                buf.writeNbt(node.getUpgradeItem(i).saveOptional(provider));
            }
        }
    }
}
