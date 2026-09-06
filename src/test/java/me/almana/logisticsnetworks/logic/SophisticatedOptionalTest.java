package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SophisticatedOptionalTest {
    @BeforeAll
    static void bootstrap() {
        TransferParityTest.bootstrap();
    }

    @Test
    void genericTransfersRemainAvailableWithoutOptionalIntegration() throws Exception {
        if (Boolean.getBoolean("logisticsnetworks.test.sophisticatedAbsent")) {
            assertFalse(ModList.get().isLoaded("sophisticatedcore"));
            assertFalse(ModList.get().isLoaded("sophisticatedstorage"));
            assertThrows(ClassNotFoundException.class, () -> Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemResourceHandler"));
        }
        var source = TransferParityTest.inventory(8);
        var target = TransferParityTest.inventory(0, 60);
        assertFalse(SophisticatedCoreCompat.isBulkHandler(target));
        assertEquals(8, TransferParityTest.move(source, List.of(target), false));
        assertEquals(0, source.getAmountAsInt(0));
        assertEquals(8, target.getAmountAsInt(0));
        assertEquals(60, target.getAmountAsInt(1));
    }

    @Test
    void unmaskedGenericHandlerControlsWholeInsertion() throws Exception {
        var source = TransferParityTest.inventory(8);
        var target = new net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler(2) {
            @Override
            public int insert(net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                    net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                return super.insert(1, resource, amount, transaction);
            }
        };
        assertFalse(SophisticatedCoreCompat.isBulkHandler(target));
        assertEquals(8, TransferParityTest.move(source, List.of(target), false));
        assertEquals(0, source.getAmountAsInt(0));
        assertEquals(0, target.getAmountAsInt(0));
        assertEquals(8, target.getAmountAsInt(1));
    }
}
