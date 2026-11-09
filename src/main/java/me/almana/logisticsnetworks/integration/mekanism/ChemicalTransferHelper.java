package me.almana.logisticsnetworks.integration.mekanism;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.IoSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.ObjLongConsumer;

public final class ChemicalTransferHelper {

    // Enable when 26.1.2 is supported.
    /*
    private static final Logger LOGGER = LogUtils.getLogger();

    static final TagKey<Chemical> RESOURCE_BLACKLIST_CHEMICALS = TagKey.create(
            MekanismAPI.CHEMICAL_REGISTRY_NAME,
            ResourceLocation.fromNamespaceAndPath("logisticsnetworks", "blacklist/chemicals"));
    */

    private ChemicalTransferHelper() {
    }

    // Enable when 26.1.2 is supported.
    /*
    @Nullable
    public static IChemicalHandler getHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        if (side != null)
            return level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, side);
        for (Direction d : Direction.values()) {
            IChemicalHandler h = level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, d);
            if (h != null) return h;
        }
        return null;
    }

    @Nullable
    public static IChemicalHandler getUnsidedHandler(ServerLevel level, BlockPos pos) {
        if (MekanismCompat.isMekanismBlock(level, pos)) return null;
        return level.getCapability(Capabilities.CHEMICAL.block(), pos, null);
    }
    */

    public static boolean hasHandler(ServerLevel level, BlockPos pos) {
        // Enable when 26.1.2 is supported.
        /*
        if (getHandler(level, pos, null) != null)
            return true;
        for (Direction dir : Direction.values()) {
            if (getHandler(level, pos, dir) != null)
                return true;
        }
        */
        return false;
    }

    public static List<String> getBlacklistedChemicalNames(ServerLevel level, BlockPos pos) {
        // Enable when 26.1.2 is supported.
        /*
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
        */
        return Collections.emptyList();
    }

    public static boolean chemicalHasTag(String chemicalId, String tagId) {
        // Enable when 26.1.2 is supported.
        /*
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
        return chemical.get().is(key);
        */
        return false;
    }

    @Nullable
    public static String getChemicalIdFromItem(ItemStack itemStack) {
        // Enable when 26.1.2 is supported.
        /*
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
        */
        return null;
    }

    @Nullable
    public static List<String> getChemicalTagsFromItem(ItemStack itemStack) {
        // Enable when 26.1.2 is supported.
        /*
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
        */
        return null;
    }

    public static long transferBetween(ServerLevel sourceLevel, BlockPos sourcePos, IoSide sourceSide,
            ServerLevel targetLevel, BlockPos targetPos, IoSide targetSide, long limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode, @Nullable ObjLongConsumer<String> onMoved) {
        // Enable when 26.1.2 is supported.
        /*
        IChemicalHandler source = sourceSide == IoSide.NONE ? getUnsidedHandler(sourceLevel, sourcePos)
                : getHandler(sourceLevel, sourcePos, sourceSide.direction());
        if (source == null) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] No source handler at {} side {}", sourcePos, sourceSide);
            return 0;
        }
        IChemicalHandler target = targetSide == IoSide.NONE ? getUnsidedHandler(targetLevel, targetPos)
                : getHandler(targetLevel, targetPos, targetSide.direction());
        if (target == null) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] No target handler at {} side {}", targetPos, targetSide);
            return 0;
        }
        if (Config.debugMode)
            LOGGER.debug("[Chemical] Transferring {} -> {}, limit={}, srcTanks={}, tgtTanks={}",
                    sourcePos, targetPos, limit, source.getChemicalTanks(), target.getChemicalTanks());
        return executeChemicalMove(source, target, limit, exportFilters, exportFilterMode,
                importFilters, importFilterMode, onMoved, null);
        */
        return 0;
    }

    @Nullable
    public static Identifier getChemicalIcon(String chemicalId) {
        // Enable when 26.1.2 is supported.
        /*
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getIcon).orElse(null);
        */
        return null;
    }

    public static int getChemicalTint(String chemicalId) {
        // Enable when 26.1.2 is supported.
        /*
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return 0xFFFFFFFF;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getTint).orElse(0xFFFFFFFF);
        */
        return 0xFFFFFFFF;
    }

    @Nullable
    public static Component getChemicalTextComponent(String chemicalId) {
        // Enable when 26.1.2 is supported.
        /*
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getTextComponent).orElse(null);
        */
        return null;
    }

    public static boolean isValidChemicalId(String chemicalId) {
        // Enable when 26.1.2 is supported.
        /*
        if (chemicalId == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null) return false;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).isPresent();
        */
        return false;
    }

    public static List<String> getAllChemicalIds() {
        // Enable when 26.1.2 is supported.
        /*
        List<String> ids = new ArrayList<>();
        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            ResourceLocation key = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            if (key != null) ids.add(key.toString());
        }
        return ids;
        */
        return Collections.emptyList();
    }

    public static List<String> getAllChemicalTags() {
        // Enable when 26.1.2 is supported.
        /*
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            chemical.getTags().forEach(t -> tags.add(t.location().toString()));
        }
        return new ArrayList<>(tags);
        */
        return Collections.emptyList();
    }

    // Enable when 26.1.2 is supported.
    /*
    @Nullable
    public static String getChemicalId(ChemicalStack stack) {
        if (stack.isEmpty())
            return null;
        ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical());
        return id != null ? id.toString() : null;
    }

    public static long transferBetween(IChemicalHandler source, IChemicalHandler target, long limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            @Nullable FilterItemData.ReadCache filterReadCache, @Nullable ObjLongConsumer<String> onMoved,
            @Nullable String requiredId) {
        if (source == null || target == null) {
            return 0;
        }
        if (Config.debugMode)
            LOGGER.debug("[Chemical] Transferring limit={}, srcTanks={}, tgtTanks={}",
                    limit, source.getChemicalTanks(), target.getChemicalTanks());
        return executeChemicalMove(source, target, limit, exportFilters, exportFilterMode,
                importFilters, importFilterMode, filterReadCache, onMoved, requiredId);
    }

    public static Map<String, Long> exportableAmounts(IChemicalHandler source, ItemStack[] exportFilters,
            FilterMode exportFilterMode, @Nullable FilterItemData.ReadCache filterReadCache) {
        Map<String, Long> byChemical = new LinkedHashMap<>();
        for (int tank = 0; tank < source.getChemicalTanks(); tank++) {
            ChemicalStack stack = source.getChemicalInTank(tank);
            if (stack.isEmpty() || stack.is(RESOURCE_BLACKLIST_CHEMICALS))
                continue;
            String chemId = getChemicalId(stack);
            if (chemId == null || !FilterLogic.matchesChemical(exportFilters, exportFilterMode, chemId, filterReadCache))
                continue;
            byChemical.merge(chemId, source.extractChemical(tank, stack.getAmount(), Action.SIMULATE).getAmount(),
                    Long::sum);
        }
        byChemical.replaceAll((chemId, amount) -> {
            long keepCap = getPerEntryChemicalAmountLimit(chemId, exportFilters, null, source, null, filterReadCache);
            return keepCap >= 0 ? Math.min(amount, keepCap) : amount;
        });
        return byChemical;
    }

    private static long executeChemicalMove(IChemicalHandler source, IChemicalHandler target, long limitAmount,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode, @Nullable ObjLongConsumer<String> onMoved,
            @Nullable String requiredId) {
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
            if (requiredId != null && !requiredId.equals(chemId))
                continue;
            if (chemId != null) {
                if (!FilterLogic.matchesChemical(exportFilters, exportFilterMode, chemId))
                    continue;
                if (!FilterLogic.matchesChemical(importFilters, importFilterMode, chemId))
                    continue;
            }

            long requestFromTank = Math.min(remaining, tankChemical.getAmount());
            ChemicalStack simulated = source.extractChemical(tank, requestFromTank, Action.SIMULATE);
            if (simulated.isEmpty()) {
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Tank {} has {} but extract simulation empty", tank,
                            tankChemical.getAmount());
                continue;
            }

            long request = Math.min(simulated.getAmount(), remaining);
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
                if (onMoved != null && chemId != null) {
                    onMoved.accept(chemId, moved);
                }
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Moved {} from tank {}", moved, tank);
            }
        }

        return limitAmount - remaining;
    }
    */

    // Enable when 26.1.2 is supported.
    /*
    Source owner: TransferEngine.transferChemicals (17aea589 equal distribution)

    List<IChemicalHandler> equalHandlers = exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN
            ? new ArrayList<>() : null;
    List<ChannelData> equalChannels = equalHandlers == null ? null : new ArrayList<>();

    IChemicalHandler targetHandler = capCache.findChemicalHandler(targetLevel, targetPos,
            target.channel().getIoSide());
    if (targetHandler == null)
        continue;
    if (equalHandlers != null) {
        equalHandlers.add(targetHandler);
        equalChannels.add(target.channel());
        continue;
    }

    long moved = ChemicalTransferHelper.transferBetween(
            sourceHandler, targetHandler, remaining,
            exportChannel.getFilterItems(), exportChannel.getFilterMode(),
            target.channel().getFilterItems(), target.channel().getFilterMode(),
            filterReadCache, onMoved, null);

    if (!anyReachable)
        return -1;
    if (equalHandlers != null) {
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        long moved = 0;
        // Split each chemical on its own
        for (Map.Entry<String, Long> stock : ChemicalTransferHelper.exportableAmounts(sourceHandler,
                exportFilters, exportChannel.getFilterMode(), filterReadCache).entrySet()) {
            if (moved >= remaining) break;
            moved += EvenSplit.deal(equalHandlers.size(), Math.min(stock.getValue(), remaining - moved),
                    (i, share) -> ChemicalTransferHelper.transferBetween(sourceHandler, equalHandlers.get(i),
                            share, exportFilters, exportChannel.getFilterMode(),
                            equalChannels.get(i).getFilterItems(), equalChannels.get(i).getFilterMode(),
                            filterReadCache, onMoved, stock.getKey()));
        }
        return (int) moved;
    }
    return batchLimit - remaining;
    */

    public static Optional<RecipeCardData.Ingredient> recipeIngredient(Object value) {
        // Enable when 26.1.2 is supported.
        /*
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(getChemicalId(stack))
                .map(id -> RecipeCardData.Ingredient.ofChemical(id, stack.getAmount()));
        */
        return Optional.empty();
    }
}
