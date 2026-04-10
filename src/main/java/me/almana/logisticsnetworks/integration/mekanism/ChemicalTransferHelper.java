package me.almana.logisticsnetworks.integration.mekanism;

import me.almana.logisticsnetworks.data.FilterMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class ChemicalTransferHelper {

    // 26.1 Mek API pending
    /*
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TagKey<Chemical> RESOURCE_BLACKLIST_CHEMICALS = TagKey.create(
            MekanismAPI.CHEMICAL_REGISTRY_NAME,
            ResourceLocation.fromNamespaceAndPath("logisticsnetworks", "blacklist/chemicals"));
    */

    private ChemicalTransferHelper() {
    }

    // 26.1 Mek API pending
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
    */

    public static boolean hasHandler(ServerLevel level, BlockPos pos) {
        // 26.1 Mek API pending
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
        // 26.1 Mek API pending
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
        // 26.1 Mek API pending
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
        // 26.1 Mek API pending
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
        // 26.1 Mek API pending
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

    public static long transferBetween(ServerLevel sourceLevel, BlockPos sourcePos, @Nullable Direction sourceSide,
            ServerLevel targetLevel, BlockPos targetPos, @Nullable Direction targetSide, long limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode) {
        // 26.1 Mek API pending
        /*
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
                importFilters, importFilterMode);
        */
        return 0;
    }

    @Nullable
    public static Identifier getChemicalIcon(String chemicalId) {
        // 26.1 Mek API pending
        /*
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getIcon).orElse(null);
        */
        return null;
    }

    public static int getChemicalTint(String chemicalId) {
        // 26.1 Mek API pending
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
        // 26.1 Mek API pending
        /*
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null)
            return null;
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(id).map(Chemical::getTextComponent).orElse(null);
        */
        return null;
    }

    // 26.1 Mek API pending
    /*
    @Nullable
    public static String getChemicalId(ChemicalStack stack) {
        if (stack.isEmpty())
            return null;
        ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical());
        return id != null ? id.toString() : null;
    }

    private static long executeChemicalMove(IChemicalHandler source, IChemicalHandler target, long limitAmount,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode) {
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
                if (Config.debugMode)
                    LOGGER.debug("[Chemical] Moved {} from tank {}", moved, tank);
            }
        }

        return limitAmount - remaining;
    }
    */
}
