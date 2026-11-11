// Enable when 26.1.2 is supported.
/*
Source owner: LogisticsNodeEntity

public void moveAttachment(BlockPos pos, Vec3 entityPos) {
    BlockPos previousPos = getAttachedPos();
    setPos(entityPos);
    setAttachedPos(pos);

    if (level() instanceof ServerLevel serverLevel && getNetworkId() != null) {
        NetworkRegistry registry = NetworkRegistry.get(serverLevel);
        registry.evictCapabilities(serverLevel, previousPos);
        registry.evictCapabilities(serverLevel, pos);
        registry.invalidateNetwork(getNetworkId());
    }
}

Source owner: LogisticsNodeEntity (4ef23ae1, drop tracked signal before a move)

public void moveAttachment(BlockPos pos, Vec3 entityPos) {
    forgetSignal();
    BlockPos previousPos = getAttachedPos();
    ...
}

Source owner: mixin configs (a496ddef + 4ef23ae1)

Sable mixins move to logisticsnetworks.sable.mixins.json (package
integration.sable.mixin, plugin SableMixinPlugin, mixins SableAssemblyMixin and
SimulatedDisassemblyMixin), registered in neoforge.mods.toml:
[[mixins]]
config="${mod_id}.sable.mixins.json"
*/
