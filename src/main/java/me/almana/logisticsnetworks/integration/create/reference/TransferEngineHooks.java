// Enable when 26.1.2 is supported.
/*
Source owner: TransferEngine

if (!node.isMountedOnCreate() && node.level() instanceof ServerLevel level) {
    signalCache.put(node.getUUID(), needsSignal ? level.getBestNeighborSignal(node.getAttachedPos()) : 0);
} else if (node.isMountedOnCreate()) {
    signalCache.put(node.getUUID(), 0);
}

if (!canRunChannel(node, channel) || !CreateCompat.isResolved(node)) {
    continue;
}

if (sourceNode.isMountedOnCreate()) {
    boolean storageAvailable = switch (channel.getType()) {
        case ITEM -> capCache.findItemHandler(sourceNode, channel.getIoDirection()) != null;
        case FLUID -> capCache.findFluidHandler(sourceNode, channel.getIoDirection()) != null;
        default -> false;
    };
    if (!storageAvailable) {
        continue;
    }
}

if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos)) {
    return -1;
}
IItemHandler sourceHandler = capCache.findItemHandler(sourceNode, exportChannel.getIoDirection());

boolean hasUsableTarget = false;
boolean hasUnavailableMountedTarget = false;
boolean hasStationaryTarget = false;

hasStationaryTarget |= !target.node.isMountedOnCreate();
if (!target.node.isMountedOnCreate() && !targetLevel.isLoaded(targetPos)) {
    continue;
}
if (!sourceNode.isMountedOnCreate() && !target.node.isMountedOnCreate()
        && isSameItemStorage(sourceLevel, sourcePos, targetLevel, targetPos)) {
    continue;
}

IItemHandler targetHandler = capCache.findItemHandler(target.node, target.channel.getIoDirection());
if (targetHandler == null) {
    hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
    continue;
}
hasUsableTarget = true;

if (shouldPauseForUnavailableMountedTargets(hasUsableTarget, hasUnavailableMountedTarget,
        hasStationaryTarget)) {
    return new ResolvedItemTargets(reachableTargets, resolvedRefs, ResolvedItemTargets.PAUSED);
}

if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos)) {
    return -1;
}
IFluidHandler sourceHandler = capCache.findFluidHandler(sourceNode, exportChannel.getIoDirection());

IFluidHandler targetHandler = capCache.findFluidHandler(target.node, target.channel.getIoDirection());
if (targetHandler == null) {
    hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
    continue;
}

public static boolean canRunChannel(boolean mounted, ChannelType type, RedstoneMode redstoneMode) {
    if (!mounted) {
        return true;
    }
    return redstoneMode == RedstoneMode.ALWAYS_ON
            && (type == ChannelType.ITEM || type == ChannelType.FLUID);
}

public static boolean canRunChannel(LogisticsNodeEntity node, ChannelData channel) {
    return canRunChannel(node.isMountedOnCreate(), channel.getType(), channel.getRedstoneMode());
}

static boolean shouldPauseForUnavailableMountedTargets(boolean hasUsableTarget,
        boolean hasUnavailableMountedTarget, boolean hasStationaryTarget) {
    return !hasUsableTarget && hasUnavailableMountedTarget && !hasStationaryTarget;
}
*/
