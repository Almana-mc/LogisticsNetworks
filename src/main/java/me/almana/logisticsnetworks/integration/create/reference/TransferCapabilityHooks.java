// Enable when 26.1.2 is supported.
/*
Source owner: TransferCapabilityCache

@Nullable
public IItemHandler findItemHandler(LogisticsNodeEntity node, @Nullable Direction direction) {
    if (node.isMountedOnCreate()) {
        return CreateCompat.findMountedItemHandler(node);
    }
    return findItemHandler((ServerLevel) node.level(), node.getAttachedPos(), direction);
}

@Nullable
public IItemHandler findBulkItemHandler(LogisticsNodeEntity node, IItemHandler sidedHandler) {
    if (node.isMountedOnCreate()) {
        return null;
    }
    return findBulkItemHandler((ServerLevel) node.level(), node.getAttachedPos(), sidedHandler);
}

@Nullable
public IFluidHandler findFluidHandler(LogisticsNodeEntity node, @Nullable Direction direction) {
    if (node.isMountedOnCreate()) {
        return CreateCompat.findMountedFluidHandler(node);
    }
    return findFluidHandler((ServerLevel) node.level(), node.getAttachedPos(), direction);
}

Source owner: AttachedStorageFilterScanner (54182a03, per-type scan merge)

public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
    if (!node.isMountedOnCreate() && !level.isLoaded(node.getAttachedPos())) {
        return new Result(0, false, false);
    }
    if (!CreateCompat.isResolved(node)) {
        return new Result(0, false, false);
    }

    TransferCapabilityCache capabilities = NetworkRegistry.get(level).getCapabilityCache();
    Result total = new Result(0, false, false);
    if (channel.hasType(ChannelType.ITEM)) {
        total = merge(total, scanItems(capabilities.findItemHandler(node, channel.getIoDirection()), filter));
    }
    if (channel.hasType(ChannelType.FLUID) && !total.filterFull()) {
        total = merge(total, scanFluids(capabilities.findFluidHandler(node, channel.getIoDirection()), filter));
    }
    if (channel.hasType(ChannelType.CHEMICAL) && !total.filterFull() && !node.isMountedOnCreate()) {
        total = merge(total, scanChemicals(level, node, channel, capabilities, filter));
    }
    return total;
}
*/
