package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import net.minecraft.client.renderer.Rect2i;

@EmiEntrypoint
public class LogisticsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(FilterScreen.class, (screen, consumer) -> {
            for (Rect2i rect : screen.getExtraAreas()) {
                consumer.accept(new Bounds(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
            }
        });
    }
}
