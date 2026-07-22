package me.almana.logisticsnetworks.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.screen.WrenchColorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchColorCommand {

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("logisticsnetworks").then(wrenchColor()));
        event.getDispatcher().register(Commands.literal("ln").then(wrenchColor()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wrenchColor() {
        return Commands.literal("wrenchColor").executes(context -> {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null) {
                return 0;
            }
            InteractionHand hand = WrenchInputHandler.findWrenchHand(player);
            if (hand == null) {
                context.getSource().sendFailure(
                        Component.translatable("message.logisticsnetworks.wrench.colors.no_wrench"));
                return 0;
            }
            minecraft.execute(() -> minecraft.setScreen(
                    new WrenchColorScreen(player.getItemInHand(hand), hand)));
            return 1;
        });
    }
}
