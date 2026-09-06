// Enable when 26.1.2 is supported.
/*
Source owner: EventHandler

if (!node.isMountedOnCreate() && node.getAttachedPos().equals(pos) && node.isActive()) {
    event.setUseBlock(TriState.FALSE);
    return;
}

if (node.isMountedOnCreate() || !node.isActive() || node.getNetworkId() == null) {
    continue;
}

if (!node.isMountedOnCreate() && node.getAttachedPos().equals(pos)) {
    if (node.getNetworkId() != null) {
        NetworkRegistry registry = NetworkRegistry.get(serverLevel);
        registry.removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
        registry.evictCapabilities(serverLevel, node.getAttachedPos());
    }
    node.discard();
}

if (!node.isMountedOnCreate()) {
    ids.addAll(MekanismCompat.getBlacklistedChemicalNames(level, node.getAttachedPos()));
}

if (!node.isMountedOnCreate() && node.isActive() && node.getNetworkId() != null
        && node.getAttachedPos().equals(containerPos)) {
    NetworkRegistry.get(level).wakeNetwork(node.getNetworkId());
}

Source owner: WrenchItem

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
        if (isSecondaryUse(player) && AE2Compat.isLoaded() && AE2Compat.isGridHost(level, clickedPos)) {
            return toggleAE2Link(context.getItemInHand(), player, level, clickedPos);
        }
        return InteractionResult.SUCCESS;
    }

    return interactWithMountedNode(node, player, context.getItemInHand());
}

public InteractionResult interactWithMountedNode(LogisticsNodeEntity node, Player player, ItemStack wrenchStack) {
    if (!node.isOwnedBy(player)) {
        player.displayClientMessage(Component.translatable("message.logisticsnetworks.not_owner"), true);
        return InteractionResult.FAIL;
    }
    if (node.getOwnerUUID() == null) {
        node.setOwnerUUID(player.getUUID());
    }
    return switch (getMode(wrenchStack)) {
        case WRENCH -> isSecondaryUse(player)
                ? removeNode(node.level(), node, player)
                : openNodeGui(node, player);
        case COPY_PASTE -> isSecondaryUse(player)
                ? pasteToNode(node, player, wrenchStack)
                : copyFromNode(node, player, wrenchStack);
        case MASS_PLACEMENT -> InteractionResult.CONSUME;
    };
}

Source owner: ServerPayloadHandler

BlockPos attachedPos = node.getAttachedPos();
BlockState state = CreateCompat.getAttachedBlockState(node);
String blockName = state.isAir()
        ? "unknown"
        : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
*/
