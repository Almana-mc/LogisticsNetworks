package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Logisticsnetworks.MOD_ID)
public class Logisticsnetworks {

    public static final String MOD_ID = "logisticsnetworks";

    public Logisticsnetworks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Registration.init(modBus);
        NetworkHandler.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "logistics-network/common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "logistics-network/client.toml");
        UpgradeLimitsConfig.load();
    }
}
