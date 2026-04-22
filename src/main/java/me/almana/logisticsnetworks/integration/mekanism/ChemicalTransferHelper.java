package me.almana.logisticsnetworks.integration.mekanism;

import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.common.capabilities.Capabilities;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.FilterLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class ChemicalTransferHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BLACKLIST_TAG_ID = ResourceLocation.fromNamespaceAndPath(
            "logisticsnetworks", "blacklist/chemicals");

    private static final String TYPE_GAS = "gas";
    private static final String TYPE_INFUSE = "infuse";
    private static final String TYPE_PIGMENT = "pigment";
    private static final String TYPE_SLURRY = "slurry";
    private static final char TYPE_SEPARATOR = '|';

    private ChemicalTransferHelper() {
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    public static IChemicalHandler getHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        if (side != null) {
            return getSideHandler(blockEntity, side);
        }

        List<IChemicalHandler> found = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            IChemicalHandler handler = getSideHandler(blockEntity, dir);
            if (handler == null) {
                continue;
            }
            boolean duplicate = false;
            for (IChemicalHandler existing : found) {
                if (existing == handler) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        if (found.size() == 1) {
            return found.get(0);
        }
        return new CombinedChemicalHandler(found.toArray(new IChemicalHandler[0]));
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    private static IChemicalHandler getSideHandler(BlockEntity blockEntity, @Nullable Direction side) {
        IChemicalHandler gas = blockEntity.getCapability(Capabilities.GAS_HANDLER, side).orElse(null);
        if (gas != null) {
            return gas;
        }

        IChemicalHandler infusion = blockEntity.getCapability(Capabilities.INFUSION_HANDLER, side).orElse(null);
        if (infusion != null) {
            return infusion;
        }

        IChemicalHandler pigment = blockEntity.getCapability(Capabilities.PIGMENT_HANDLER, side).orElse(null);
        if (pigment != null) {
            return pigment;
        }

        return blockEntity.getCapability(Capabilities.SLURRY_HANDLER, side).orElse(null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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
                running += handlers[i].getTanks();
            }
            this.totalTanks = running;
        }

        private int handlerIndex(int tank) {
            for (int i = handlers.length - 1; i >= 0; i--) {
                if (tank >= tankOffsets[i]) {
                    return i;
                }
            }
            return 0;
        }

        @Override
        public ChemicalStack getEmptyStack() {
            return handlers[0].getEmptyStack();
        }

        @Override
        public int getTanks() {
            return totalTanks;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            if (tank < 0 || tank >= totalTanks) {
                return getEmptyStack();
            }
            int index = handlerIndex(tank);
            return handlers[index].getChemicalInTank(tank - tankOffsets[index]);
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
            if (tank < 0 || tank >= totalTanks) {
                return;
            }
            int index = handlerIndex(tank);
            handlers[index].setChemicalInTank(tank - tankOffsets[index], stack);
        }

        @Override
        public long getTankCapacity(int tank) {
            if (tank < 0 || tank >= totalTanks) {
                return 0;
            }
            int index = handlerIndex(tank);
            return handlers[index].getTankCapacity(tank - tankOffsets[index]);
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            if (tank < 0 || tank >= totalTanks) {
                return false;
            }
            int index = handlerIndex(tank);
            return handlers[index].isValid(tank - tankOffsets[index], stack);
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (tank < 0 || tank >= totalTanks) {
                return stack;
            }
            int index = handlerIndex(tank);
            return handlers[index].insertChemical(tank - tankOffsets[index], stack, action);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            if (tank < 0 || tank >= totalTanks) {
                return getEmptyStack();
            }
            int index = handlerIndex(tank);
            return handlers[index].extractChemical(tank - tankOffsets[index], amount, action);
        }
    }

    public static boolean hasHandler(ServerLevel level, BlockPos pos) {
        if (getHandler(level, pos, null) != null) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (getHandler(level, pos, dir) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<String> getBlacklistedChemicalNames(ServerLevel level, BlockPos pos) {
        List<String> names = new ArrayList<>();
        IChemicalHandler handler = getHandler(level, pos, null);
        if (handler == null) {
            return names;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> stack = (ChemicalStack<?>) handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && isBlacklistedChemical(stack)) {
                String name = stack.getTextComponent().getString();
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Nullable
    public static String getChemicalId(ChemicalStack<?> stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ResourceLocation id = stack.getTypeRegistryName();
        if (id == null) {
            return null;
        }

        String type = getChemicalType(stack.getType());
        return type == null ? id.toString() : type + TYPE_SEPARATOR + id;
    }

    public static boolean chemicalHasTag(String chemicalId, String tagId) {
        if (chemicalId == null || tagId == null) {
            return false;
        }
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
        if (tagLoc == null) {
            return false;
        }

        return resolveChemicals(chemicalId).stream()
                .anyMatch(chemical -> chemical.getTags().anyMatch(tag -> tag.location().equals(tagLoc)));
    }

    @Nullable
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static String getChemicalIdFromItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        IChemicalHandler handler = getItemHandler(itemStack);
        if (handler == null) {
            return null;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> stack = (ChemicalStack<?>) handler.getChemicalInTank(tank);
            if (!stack.isEmpty()) {
                return getChemicalId(stack);
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<String> getChemicalTagsFromItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        IChemicalHandler handler = getItemHandler(itemStack);
        if (handler == null) {
            return null;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> stack = (ChemicalStack<?>) handler.getChemicalInTank(tank);
            if (!stack.isEmpty()) {
                return stack.getType().getTags().map(tag -> tag.location().toString()).toList();
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public static long transferBetween(ServerLevel sourceLevel, BlockPos sourcePos, @Nullable Direction sourceSide,
            ServerLevel targetLevel, BlockPos targetPos, @Nullable Direction targetSide, long limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        IChemicalHandler source = getHandler(sourceLevel, sourcePos, sourceSide);
        if (source == null) {
            if (Config.debugMode) {
                LOGGER.debug("[Chemical] No source handler at {} side {}", sourcePos, sourceSide);
            }
            return 0;
        }
        IChemicalHandler target = getHandler(targetLevel, targetPos, targetSide);
        if (target == null) {
            if (Config.debugMode) {
                LOGGER.debug("[Chemical] No target handler at {} side {}", targetPos, targetSide);
            }
            return 0;
        }
        if (Config.debugMode) {
            LOGGER.debug("[Chemical] Transferring {} -> {}, limit={}, srcTanks={}, tgtTanks={}",
                    sourcePos, targetPos, limit, source.getTanks(), target.getTanks());
        }
        return executeChemicalMove(source, target, limit, exportFilters, exportFilterMode, importFilters,
                importFilterMode, filterReadCache);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static long executeChemicalMove(IChemicalHandler source, IChemicalHandler target, long limitAmount,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        long remaining = limitAmount;

        for (int tank = 0; tank < source.getTanks(); tank++) {
            if (remaining <= 0) {
                break;
            }

            ChemicalStack<?> tankChemical = (ChemicalStack<?>) source.getChemicalInTank(tank);
            if (tankChemical.isEmpty() || isBlacklistedChemical(tankChemical)) {
                continue;
            }

            String chemId = getChemicalId(tankChemical);
            if (chemId != null) {
                if (!FilterLogic.matchesChemical(exportFilters, exportFilterMode, chemId, filterReadCache)) {
                    continue;
                }
                if (!FilterLogic.matchesChemical(importFilters, importFilterMode, chemId, filterReadCache)) {
                    continue;
                }
            }

            long allowedByAmount = remaining;
            if (chemId != null) {
                long stockAllowed = getPerEntryChemicalAmountLimit(chemId, exportFilters, importFilters, source, target,
                        filterReadCache);
                if (stockAllowed == 0) {
                    continue;
                }
                if (stockAllowed > 0) {
                    allowedByAmount = Math.min(allowedByAmount, stockAllowed);
                }
            }

            long requestFromTank = Math.min(allowedByAmount, tankChemical.getAmount());
            ChemicalStack<?> simulated = (ChemicalStack<?>) source.extractChemical(tank, requestFromTank,
                    Action.SIMULATE);
            if (simulated.isEmpty()) {
                if (Config.debugMode) {
                    LOGGER.debug("[Chemical] Tank {} has {} but extract simulation empty", tank,
                            tankChemical.getAmount());
                }
                continue;
            }

            long request = Math.min(simulated.getAmount(), allowedByAmount);
            if (chemId != null) {
                int perEntryBatch = getPerEntryChemicalBatchLimit(chemId, exportFilters, importFilters,
                        filterReadCache);
                if (perEntryBatch > 0) {
                    request = Math.min(request, perEntryBatch);
                }
            }
            ChemicalStack<?> requestStack = simulated.copy();
            requestStack.setAmount(request);

            ChemicalStack<?> insertRemainder = (ChemicalStack<?>) target.insertChemical(requestStack, Action.SIMULATE);
            long accepted = request - (insertRemainder.isEmpty() ? 0 : insertRemainder.getAmount());
            if (accepted <= 0) {
                if (Config.debugMode) {
                    LOGGER.debug("[Chemical] Tank {} target rejected insertion (request={})", tank, request);
                }
                continue;
            }

            ChemicalStack<?> extractSim = (ChemicalStack<?>) source.extractChemical(tank, accepted, Action.SIMULATE);
            long toMove = Math.min(accepted, extractSim.getAmount());
            if (toMove <= 0) {
                continue;
            }

            ChemicalStack<?> extracted = (ChemicalStack<?>) source.extractChemical(tank, toMove, Action.EXECUTE);
            if (extracted.isEmpty()) {
                continue;
            }

            ChemicalStack<?> remainder = (ChemicalStack<?>) target.insertChemical(extracted, Action.EXECUTE);
            long moved = extracted.getAmount() - (remainder.isEmpty() ? 0 : remainder.getAmount());

            if (!remainder.isEmpty()) {
                source.insertChemical(remainder, Action.EXECUTE);
            }

            if (moved > 0) {
                remaining -= moved;
                if (Config.debugMode) {
                    LOGGER.debug("[Chemical] Moved {} from tank {}", moved, tank);
                }
            }
        }

        return limitAmount - remaining;
    }

    @Nullable
    public static ResourceLocation getChemicalIcon(String chemicalId) {
        return resolveFirstChemical(chemicalId).map(Chemical::getIcon).orElse(null);
    }

    public static int getChemicalTint(String chemicalId) {
        return resolveFirstChemical(chemicalId).map(Chemical::getTint).orElse(0xFFFFFFFF);
    }

    @Nullable
    public static Component getChemicalTextComponent(String chemicalId) {
        return resolveFirstChemical(chemicalId).map(Chemical::getTextComponent).orElse(null);
    }

    private static boolean isBlacklistedChemical(ChemicalStack<?> stack) {
        return stack.getType().getTags().anyMatch(tag -> tag.location().equals(BLACKLIST_TAG_ID));
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    private static IChemicalHandler getItemHandler(ItemStack itemStack) {
        IChemicalHandler gas = itemStack.getCapability(Capabilities.GAS_HANDLER).orElse(null);
        if (gas != null) {
            return gas;
        }

        IChemicalHandler infusion = itemStack.getCapability(Capabilities.INFUSION_HANDLER).orElse(null);
        if (infusion != null) {
            return infusion;
        }

        IChemicalHandler pigment = itemStack.getCapability(Capabilities.PIGMENT_HANDLER).orElse(null);
        if (pigment != null) {
            return pigment;
        }

        return itemStack.getCapability(Capabilities.SLURRY_HANDLER).orElse(null);
    }

    @Nullable
    private static String getChemicalType(Chemical<?> chemical) {
        if (chemical instanceof Gas) {
            return TYPE_GAS;
        }
        if (chemical instanceof InfuseType) {
            return TYPE_INFUSE;
        }
        if (chemical instanceof Pigment) {
            return TYPE_PIGMENT;
        }
        if (chemical instanceof Slurry) {
            return TYPE_SLURRY;
        }
        return null;
    }

    private static java.util.Optional<Chemical<?>> resolveFirstChemical(@Nullable String chemicalId) {
        List<Chemical<?>> matches = resolveChemicals(chemicalId);
        return matches.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(matches.get(0));
    }

    private static List<Chemical<?>> resolveChemicals(@Nullable String chemicalId) {
        List<Chemical<?>> matches = new ArrayList<>();
        if (chemicalId == null || chemicalId.isEmpty()) {
            return matches;
        }

        String type = null;
        String idPart = chemicalId;
        int sep = chemicalId.indexOf(TYPE_SEPARATOR);
        if (sep > 0 && sep < chemicalId.length() - 1) {
            type = chemicalId.substring(0, sep);
            idPart = chemicalId.substring(sep + 1);
        }

        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            return matches;
        }

        if (type != null) {
            Chemical<?> exact = lookupByType(type, id);
            if (exact != null) {
                matches.add(exact);
            }
            return matches;
        }

        Chemical<?> gas = MekanismAPI.gasRegistry().getValue(id);
        if (gas != null) {
            matches.add(gas);
        }
        Chemical<?> infuse = MekanismAPI.infuseTypeRegistry().getValue(id);
        if (infuse != null) {
            matches.add(infuse);
        }
        Chemical<?> pigment = MekanismAPI.pigmentRegistry().getValue(id);
        if (pigment != null) {
            matches.add(pigment);
        }
        Chemical<?> slurry = MekanismAPI.slurryRegistry().getValue(id);
        if (slurry != null) {
            matches.add(slurry);
        }

        return matches;
    }

    public static boolean isValidChemicalId(String chemicalId) {
        return resolveFirstChemical(chemicalId).isPresent();
    }

    @SuppressWarnings("rawtypes")
    public static List<String> getAllChemicalIds() {
        List<String> ids = new ArrayList<>();
        for (Gas gas : MekanismAPI.gasRegistry()) {
            ResourceLocation key = MekanismAPI.gasRegistry().getKey(gas);
            if (key != null) {
                ids.add(TYPE_GAS + TYPE_SEPARATOR + key);
            }
        }
        for (InfuseType infuse : MekanismAPI.infuseTypeRegistry()) {
            ResourceLocation key = MekanismAPI.infuseTypeRegistry().getKey(infuse);
            if (key != null) {
                ids.add(TYPE_INFUSE + TYPE_SEPARATOR + key);
            }
        }
        for (Pigment pigment : MekanismAPI.pigmentRegistry()) {
            ResourceLocation key = MekanismAPI.pigmentRegistry().getKey(pigment);
            if (key != null) {
                ids.add(TYPE_PIGMENT + TYPE_SEPARATOR + key);
            }
        }
        for (Slurry slurry : MekanismAPI.slurryRegistry()) {
            ResourceLocation key = MekanismAPI.slurryRegistry().getKey(slurry);
            if (key != null) {
                ids.add(TYPE_SLURRY + TYPE_SEPARATOR + key);
            }
        }
        return ids;
    }

    public static List<String> getAllChemicalTags() {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        for (Gas gas : MekanismAPI.gasRegistry()) {
            gas.getTags().forEach(t -> tags.add(t.location().toString()));
        }
        for (InfuseType infuse : MekanismAPI.infuseTypeRegistry()) {
            infuse.getTags().forEach(t -> tags.add(t.location().toString()));
        }
        for (Pigment pigment : MekanismAPI.pigmentRegistry()) {
            pigment.getTags().forEach(t -> tags.add(t.location().toString()));
        }
        for (Slurry slurry : MekanismAPI.slurryRegistry()) {
            slurry.getTags().forEach(t -> tags.add(t.location().toString()));
        }
        return new ArrayList<>(tags);
    }

    @SuppressWarnings("rawtypes")
    private static long countMatchingChemical(IChemicalHandler handler, String chemicalId) {
        long amount = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            ChemicalStack<?> stack = (ChemicalStack<?>) handler.getChemicalInTank(i);
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
                    if (exportCap <= 0) {
                        return 0;
                    }
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
                    if (importCap <= 0) {
                        return 0;
                    }
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
    private static Chemical<?> lookupByType(String type, ResourceLocation id) {
        return switch (type) {
            case TYPE_GAS -> MekanismAPI.gasRegistry().getValue(id);
            case TYPE_INFUSE -> MekanismAPI.infuseTypeRegistry().getValue(id);
            case TYPE_PIGMENT -> MekanismAPI.pigmentRegistry().getValue(id);
            case TYPE_SLURRY -> MekanismAPI.slurryRegistry().getValue(id);
            default -> null;
        };
    }
}
