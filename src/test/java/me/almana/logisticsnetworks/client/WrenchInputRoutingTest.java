package me.almana.logisticsnetworks.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WrenchInputRoutingTest {
    @Test
    void cancelsVanillaUseOnlyForRemappedRelevantInput() {
        assertTrue(WrenchInputHandler.shouldCancelUse(true, false, false));
        assertFalse(WrenchInputHandler.shouldCancelUse(false, false, false));
        assertFalse(WrenchInputHandler.shouldCancelUse(true, true, false));
        assertFalse(WrenchInputHandler.shouldCancelUse(true, false, true));
    }

    @Test
    void connectedPasteRunsOnceForVanillaOrForwardedUse() {
        assertTrue(WrenchInputHandler.shouldTryConnectedPaste(true, true, false));
        assertTrue(WrenchInputHandler.shouldTryConnectedPaste(true, false, true));
        assertFalse(WrenchInputHandler.shouldTryConnectedPaste(true, false, false));
        assertFalse(WrenchInputHandler.shouldTryConnectedPaste(false, true, true));
    }
}
