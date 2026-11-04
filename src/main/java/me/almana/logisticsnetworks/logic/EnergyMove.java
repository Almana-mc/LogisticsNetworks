package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

final class EnergyMove {

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
        int extracted = source.extractEnergy(limit, true);
        if (extracted <= 0)
            return 0;

        int accepted = target.receiveEnergy(extracted, true);
        if (accepted <= 0)
            return 0;

        int toMove = Math.min(extracted, accepted);
        int actuallyExtracted = source.extractEnergy(toMove, false);
        if (actuallyExtracted <= 0)
            return 0;

        int received = target.receiveEnergy(actuallyExtracted, false);
        if (received < actuallyExtracted) {
            int rollbackAmount = actuallyExtracted - received;
            source.receiveEnergy(rollbackAmount, false);
        }
        return received;
    }
}
