package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

@EmiEntrypoint
public class LogisticsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FilterScreen.class, new FilterDragDrop());
        registry.addExclusionArea(NodeGraphScreen.class, (screen, consumer) -> {
            var window = Minecraft.getInstance().getWindow();
            consumer.accept(new Bounds(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight()));
        });
        registry.addExclusionArea(NodeScreen.class, (screen, consumer) -> {
            for (Rect2i rect : screen.getUpgradePickerAreas()) {
                consumer.accept(new Bounds(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
            }
        });
        registry.addExclusionArea(FilterScreen.class, (screen, consumer) -> {
            for (Rect2i rect : screen.getExtraAreas()) {
                consumer.accept(new Bounds(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
            }
        });
    }
}
