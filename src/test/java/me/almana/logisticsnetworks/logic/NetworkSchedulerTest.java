package me.almana.logisticsnetworks.logic;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSchedulerTest {

    @Test
    void declaresSeparatePreAndPostTickHandlers() throws Exception {
        assertTickHandler("onServerTickPre", ServerTickEvent.Pre.class);
        assertTickHandler("onServerTickPost", ServerTickEvent.Post.class);
    }

    private static void assertTickHandler(String name, Class<?> eventType) throws Exception {
        Method method = NetworkScheduler.class.getDeclaredMethod(name, eventType);

        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertNotNull(method.getAnnotation(SubscribeEvent.class));
    }
}
