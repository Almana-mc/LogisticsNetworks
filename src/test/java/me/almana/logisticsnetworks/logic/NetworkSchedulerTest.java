package me.almana.logisticsnetworks.logic;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSchedulerTest {

    @Test
    void declaresSeparatePreAndPostTickHandlers() throws Exception {
        assertTickHandler("onServerTickPre", ServerTickEvent.Pre.class);
        assertTickHandler("onServerTickPost", ServerTickEvent.Post.class);
    }

    @Test
    void declaresOnlyPreAndPostServerTickHandlers() {
        long handlers = Arrays.stream(NetworkScheduler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> ServerTickEvent.class.isAssignableFrom(method.getParameterTypes()[0]))
                .count();

        assertEquals(2L, handlers);
    }

    private static void assertTickHandler(String name, Class<?> eventType) throws Exception {
        Method method = NetworkScheduler.class.getDeclaredMethod(name, eventType);

        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertNotNull(method.getAnnotation(SubscribeEvent.class));
    }
}
