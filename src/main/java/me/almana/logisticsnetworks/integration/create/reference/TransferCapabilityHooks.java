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

Source owner: AttachedStorageFilterScanner

public static Result scan(ServerLevel level, LogisticsNodeEntity node, ChannelData channel, ItemStack filter) {
    if (!node.isMountedOnCreate() && !level.isLoaded(node.getAttachedPos())) {
        return new Result(0, false, false);
    }
    if (!CreateCompat.isResolved(node)) {
        return new Result(0, false, false);
    }

    TransferCapabilityCache capabilities = NetworkRegistry.get(level).getCapabilityCache();
    FilterTargetType target = FilterTargetType.forChannel(channel.getType());
    if (target == null) {
        return new Result(0, false, false);
    }

    return switch (target) {
        case ITEMS -> scanItems(capabilities.findItemHandler(node, channel.getIoDirection()),
                filter, level.registryAccess());
        case FLUIDS -> scanFluids(capabilities.findFluidHandler(node, channel.getIoDirection()), filter);
        case CHEMICALS -> node.isMountedOnCreate()
                ? new Result(0, false, false)
                : scanChemicals(level, node, channel, capabilities, filter);
    };
}
*/
