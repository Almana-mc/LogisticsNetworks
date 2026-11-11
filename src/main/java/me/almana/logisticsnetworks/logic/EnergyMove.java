package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.List;

final class EnergyMove {

    // Creative sources never drain
    private static final int MAX_CALLS = 64;
    // Half survives Mekanism rounding
    private static final int SATURATED = Integer.MAX_VALUE / 2;

    private EnergyMove() {
    }

    static long move(EnergyHandler source, EnergyHandler target, long limit) {
        long moved = 0;
        for (int call = 0; call < MAX_CALLS && moved < limit; call++) {
            int step = once(source, target, (int) Math.min(limit - moved, Integer.MAX_VALUE));
            moved += step;
            if (step < SATURATED) break;
        }
        return moved;
    }

    static long deal(EnergyHandler source, List<EnergyHandler> targets, long budget) {
        long moved = 0;
        for (int round = 0; round < MAX_CALLS && moved < budget; round++) {
            // Stored, not per-call extract
            long stock = Math.min(budget - moved, source.getAmountAsLong());
            long step = EvenSplit.deal(targets.size(), stock,
                    (i, share) -> once(source, targets.get(i), (int) Math.min(share, Integer.MAX_VALUE)));
            moved += step;
            if (stock < Integer.MAX_VALUE || step < stock / 2) break;
        }
        return moved;
    }

    private static int once(EnergyHandler source, EnergyHandler target, int limit) {
        return EnergyHandlerUtil.move(source, target, limit, null);
    }
}
