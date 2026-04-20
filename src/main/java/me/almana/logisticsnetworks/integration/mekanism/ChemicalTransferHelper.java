package me.almana.logisticsnetworks.integration.mekanism;

import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.FilterLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChemicalTransferHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TagKey<Chemical> RESOURCE_BLACKLIST_CHEMICALS = TagKey.create(
            MekanismAPI.CHEMICAL_REGISTRY_NAME,
            ResourceLocation.fromNamespaceAndPath("logisticsnetworks", "blacklist/chemicals"));

    private ChemicalTransferHelper() {
    }

    @Nullable
    public static IChemicalHandler getHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        if (side != null)
            return level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, side);
        List<IChemicalHandler> found = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            IChemicalHandler h = level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, d);
            if (h == null) continue;
            boolean dup = false;
            for (IChemicalHandler existing : found) {
                if (existing == h) { dup = true; break; }
            }
            if (!dup) found.add(h);
        }
        if (found.isEmpty()) return null;
        if (found.size() == 1) return found.get(0);
        return new CombinedChemicalHandler(found.toArray(new IChemicalHandler[0]));
    }

    private static final class CombinedChemicalHandler implements IChemicalHandler {
        private final IChemicalHandler[] handlers;
        private final int[] tankOffsets;
        private final int totalTanks;

        CombinedChemicalHandler(IChemicalHandler[] handlers) {
            this.handlers = handlers;
            this.tankOffsets = new int[handlers.length];
            int running = 0;
            for (int i = 0; i < handlers.length; i++) {
                tankOffsets[i] = running;
                running += handlers[i].getChemicalTanks();
            }
            this.totalTanks = running;
        }

        private int handlerIndex(int tank) {
            for (int i = handlers.length - 1; i >= 0; i--) {
                if (tank >= tankOffsets[i]) return i;
            }
            return 0;
        }

        @Override
        public int getChemicalTanks() {
            return totalTanks;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            if (tank < 0 || tank >= totalTanks) return ChemicalStack.EMPTY;
            int i = handlerIndex(tank);
            return handlers[i].getChemicalInTank(tank - tankOffsets[i]);
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
            if (tank < 0 || tank >= totalTanks) return;
            int i = handlerIndex(tank);
            handlers[i].setChemicalInTank(tank - tankOffsets[i], stack);
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            if (tank < 0 || tank >= totalTanks) return 0;
            int i = handlerIndex(tank);
            return handlers[i].getChemicalTankCapacity(tank - tankOffsets[i]);
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            if (tank < 0 || tank >= totalTanks) return false;
            int i = handlerIndex(tank);
            return handlers[i].isValid(tank - tankOffsets[i], stack);
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (tank < 0 || tank >= totalTanks) return stack;
            int i = handlerIndex(tank);
            return handlers[i].insertChemical(tank - tankOffsets[i], stack, action);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            if (tank < 0 || tank >= totalTanks) return ChemicalStack.EMPTY;
            int i = handlerIndex(tank);
            return handlers[i].extractChemical(tank - tankOffsets[i], amount, action);
        }
    }

    public static boolean hasHandler(ServerLevel level, BlockPos pos) {
        if (getHandler(level, pos, null) != null)
            return true;
        for (Direction dir : Direction.values()) {
            if (getHandler(level, pos, dir) != null)
                return true;
        }
        return false;
    }

    public static List<String> getBlacklistedChemicalNames(ServerLevel level, BlockPos pos) {
        List<String> names = new ArrayList<>();
        IChemicalHandler handler = getHandler(level, pos, null);
        if (handler == null)
            return names;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && stack.is(RESOURCE_BLACKLIST_CHEMICALS)) {
                String name = stack.getTextComponent().getString();
                if (!names.contains(name))
                    names.add(name);
            }
        }
        return names;
    }

    @Nullable
    public static String getChemicalId(ChemicalStack stack) {
        if (stack.isEmpty())
            return null;
        ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical());
        return id != null ? id.toString() : null;
    }

    public static boolean chemicalHasTag(String chemicalId, String tagId) {
        if (chemicalId == null || tagId == null)
            return false;
        ResourceLocation chemLoc = ResourceLocation.tryParse(chemicalId);
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
        if (chemLoc == null || tagLoc == null)
            return false;

        Optional<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.getOptional(chemLoc);
        if (chemical.isEmpty())
            return false;

        TagKey<Chemical> key = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagLoc);
        return MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(chemical.get()).is(key);
    }

    @Nullable
    public static String getChemicalIdFromItem(ItemStack itemStack) {
        if (itemStack.isEmpty())
            return null;
        var handler = itemStack.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.item());
        if (handler == null)
            return null;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty()) {
                return getChemicalId(stack);
            }
        }
        return null;
    }

    @Nullable
    public static List<String> getChemicalTagsFromItem(ItemStack itemStack) {
        if (itemStack.isEmpty())
            return null;
        var handler = itemStack.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.item());
        if (handler == null)
            return null;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty()) {
                return stack.getTags().map(tag -> tag.location().toString()).toList();
            }
        }
        return null;
    }

    public static long transferBetween(ServerLevel sourceLevel, BlockPos sourcePos, @Nullable Direction sourceSide,
            ServerLevel targetLevel, BlockPos targetPos, @Nullable Direction targetSide, long limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        IChemicalHandler source = getHandler(sourceLevel, sourcePos, sourceSide);
        if (source == null) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] No source handler at {} side {}", sourcePos, sourceSide);
            return 0;
        }
        IChemicalHandler target = getHandler(targetLevel, targetPos, targetSide);
        if (target == null) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] No target handler at {} side {}", targetPos, targetSide);
            return 0;
        }
        if (Config.debugMode)
            LOGGER.debug("[Chemical] Transferring {} -> {}, limit={}, srcTanks={}, tgtTanks={}",
                    sourcePos, targetPos, limit, source.getChemicalTanks(), target.getChemicalTanks());
        return executeChemicalMove(source, target, limit, exportFilters, exportFilterMode,
                importFilters, importFilterMode, filterReadCache);
    }

    private static long executeChemicalMove(IChemicalHandler source, IChemicalHandler target, long limitAmount,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        long remaining = limitAmount;

        for (int tank = 0; tank < source.getChemicalTanks(); tank++) {
            if (remaining <= 0)
                break;

            ChemicalStack tankChemical = source.getChemicalInTank(tank);
            if (tankChemical.isEmpty())
                continue;
            if (tankChemical.is(RESOURCE_BLACKLIST_CHEMICALS))
                continue;

            String chemId = getChemicalId(tankChemical);
            if (chemId != null) {
                if (!FilterLogic.matchesChemical(exportFilters, exportFilterMode, chemId, filterReadCache))
                    continue;
                if (!FilterLogic.matchesChemical(importFilters, importFilterMode, chemId, filterReadCache))
                    continue;
            }

            long allowedByAmount = remaining;
            if (chemId != null) {
                long stockAllowed = getPerEntryChemicalAmountLimit(chemId, exportFilters, importFilters, source, target,
                        filterReadCache);
                if (stockAllowed == 0) continue;
                if (stockAllowed > 0) allowedByAmount = Math.min(allowedByAmount, stockAllowed);
            }

            long requestFromTank = Math.min(allowedByAmount, tankChemical.getAmount());
            ChemicalStack simulated = source.extractChemical(tank, requestFromTank, Action.SIMULATE);
            if (simulated.isEmpty()) {
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Tank {} has {} but extract simulation empty", tank,
                            tankChemical.getAmount());
                continue;
            }

            long request = Math.min(simulated.getAmount(), allowedByAmount);
            if (chemId != null) {
                int perEntryBatch = getPerEntryChemicalBatchLimit(chemId, exportFilters, importFilters, filterReadCache);
                if (perEntryBatch > 0) {
                    request = Math.min(request, perEntryBatch);
                }
            }
            ChemicalStack insertRemainder = target.insertChemical(
                    simulated.copyWithAmount(request), Action.SIMULATE);
            long accepted = request - (insertRemainder.isEmpty() ? 0 : insertRemainder.getAmount());
            if (accepted <= 0) {
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Tank {} target rejected insertion (request={})", tank, request);
                continue;
            }

            long toMove = Math.min(accepted,
                    source.extractChemical(tank, accepted, Action.SIMULATE).getAmount());
            if (toMove <= 0)
                continue;

            ChemicalStack extracted = source.extractChemical(tank, toMove, Action.EXECUTE);
            if (extracted.isEmpty())
                continue;

            ChemicalStack remainder = target.insertChemical(extracted, Action.EXECUTE);
            long moved = extracted.getAmount() - (remainder.isEmpty() ? 0 : remainder.getAmount());

            if (!remainder.isEmpty()) {
                source.insertChemical(remainder, Action.EXECUTE);
            }

            if (moved > 0) {
                remaining -= moved;
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Moved {} from tank {}", moved, tank);
            }
        }

        return limitAmount - remaining;
    }

    public static boolean isValidChemicalId(String chemicalId) {
        if (chemicalId == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null) return false;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).isPresent();
    }

    public static List<String> getAllChemicalIds() {
        List<String> ids = new ArrayList<>();
        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            ResourceLocation key = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            if (key != null) ids.add(key.toString());
        }
        return ids;
    }

    public static List<String> getAllChemicalTags() {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(chemical).tags().forEach(t -> tags.add(t.location().toString()));
        }
        return new ArrayList<>(tags);
    }

    private static long countMatchingChemical(IChemicalHandler handler, String chemicalId) {
        long amount = 0;
        for (int i = 0; i < handler.getChemicalTanks(); i++) {
            ChemicalStack stack = handler.getChemicalInTank(i);
            if (!stack.isEmpty()) {
                String id = getChemicalId(stack);
                if (chemicalId.equals(id)) {
                    amount += stack.getAmount();
                }
            }
        }
        return amount;
    }

    private static long getPerEntryChemicalAmountLimit(String chemicalId, ItemStack[] exportFilters,
            ItemStack[] importFilters, IChemicalHandler source, IChemicalHandler target,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        long allowed = Long.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getChemicalAmountThresholdFull(filter, chemicalId, filterReadCache);
                if (threshold > 0) {
                    long sourceAmount = countMatchingChemical(source, chemicalId);
                    long exportCap = sourceAmount - threshold;
                    if (exportCap <= 0) return 0;
                    allowed = Math.min(allowed, exportCap);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int threshold = FilterItemData.getChemicalAmountThresholdFull(filter, chemicalId, filterReadCache);
                if (threshold > 0) {
                    long targetAmount = countMatchingChemical(target, chemicalId);
                    long importCap = threshold - targetAmount;
                    if (importCap <= 0) return 0;
                    allowed = Math.min(allowed, importCap);
                }
            }
        }

        return allowed == Long.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    private static int getPerEntryChemicalBatchLimit(String chemicalId, ItemStack[] exportFilters,
            ItemStack[] importFilters, @Nullable FilterItemData.ReadCache filterReadCache) {
        int limit = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int batch = FilterItemData.getChemicalBatchLimitFull(filter, chemicalId, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int batch = FilterItemData.getChemicalBatchLimitFull(filter, chemicalId, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        return limit == Integer.MAX_VALUE ? -1 : limit;
    }

    @Nullable
    public static ResourceLocation getChemicalIcon(String chemicalId) {
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getIcon).orElse(null);
    }

    public static int getChemicalTint(String chemicalId) {
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return 0xFFFFFFFF;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getTint).orElse(0xFFFFFFFF);
    }

    @Nullable
    public static Component getChemicalTextComponent(String chemicalId) {
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getTextComponent).orElse(null);
    }
}
