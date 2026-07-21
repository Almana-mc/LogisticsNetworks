package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.client.ConfigScreenRegistrar;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Logisticsnetworks.MOD_ID)
public class Logisticsnetworks {

    public static final String MOD_ID = "logisticsnetworks";

    public Logisticsnetworks(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        Registration.init(modBus);
        NetworkHandler.register();
        modBus.addListener(this::commonSetup);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "logistics-network/common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "logistics-network/client.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ConfigScreenRegistrar.register();
        }

        UpgradeLimitsConfig.load();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AE2Compat::registerLinkable);
    }
}
