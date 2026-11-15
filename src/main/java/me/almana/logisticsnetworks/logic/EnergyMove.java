package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import net.minecraftforge.energy.IEnergyStorage;
import org.slf4j.Logger;

import java.util.List;

final class EnergyMove {

    private static final Logger LOGGER = LogUtils.getLogger();
    // Creative sources never drain
    private static final int MAX_CALLS = 64;
    // Half survives Mekanism rounding
    private static final int SATURATED = Integer.MAX_VALUE / 2;

    private EnergyMove() {
    }

    static long move(IEnergyStorage source, IEnergyStorage target, long limit) {
        long moved = 0;
        for (int call = 0; call < MAX_CALLS && moved < limit; call++) {
            int step = once(source, target, (int) Math.min(limit - moved, Integer.MAX_VALUE));
            moved += step;
            if (step < SATURATED) break;
        }
        return moved;
    }

    static long deal(IEnergyStorage source, List<IEnergyStorage> targets, long budget) {
        long moved = 0;
        for (int round = 0; round < MAX_CALLS && moved < budget; round++) {
            // Stored, not per-call extract
            long stock = Math.min(budget - moved, source.getEnergyStored());
            long step = EvenSplit.deal(targets.size(), stock,
                    (i, share) -> once(source, targets.get(i), (int) share));
            moved += step;
            if (stock < Integer.MAX_VALUE || step < stock / 2) break;
        }
        return moved;
    }

    private static int once(IEnergyStorage source, IEnergyStorage target, int limit) {
        int toMove = getEnergyMoveAmount(source, target, limit);
        if (toMove <= 0)
            return 0;

        int extracted = source.extractEnergy(toMove, false);
        if (extracted <= 0)
            return 0;

        int received = target.receiveEnergy(extracted, false);
        if (received == extracted)
            return received;

        int rollbackAmount = extracted - received;
        if (source.canReceive()) {
            int returned = source.receiveEnergy(rollbackAmount, false);
            rollbackAmount -= returned;
        }

        if (rollbackAmount > 0 && Config.debugMode) {
            LOGGER.warn("ENERGY TRANSFER MISMATCH: {} RF could not be inserted or returned.", rollbackAmount);
        }

        return received;
    }

    private static int getEnergyMoveAmount(IEnergyStorage source, IEnergyStorage target, int limitRF) {
        if (limitRF <= 0)
            return 0;

        int sourceStored = Math.max(0, source.getEnergyStored());
        int targetSpace = getEnergySpace(target);
        int limit = Math.min(limitRF, Math.min(sourceStored, targetSpace));
        if (limit <= 0)
            return 0;

        int extracted = source.extractEnergy(limit, true);
        if (extracted <= 0)
            return 0;

        int accepted = target.receiveEnergy(Math.min(limit, extracted), true);
        return Math.max(0, Math.min(limit, Math.min(extracted, accepted)));
    }

    private static int getEnergySpace(IEnergyStorage target) {
        long space = (long) target.getMaxEnergyStored() - target.getEnergyStored();
        if (space <= 0)
            return 0;
        return space > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) space;
    }
}
