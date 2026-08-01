plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-neoforge" /* [SC] DO NOT EDIT */

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')

    constants.match(loader, "forge", "neoforge")

    // ResourceLocation was renamed to Identifier in 26.x
    replacements.string(current.parsed < "26") {
        replace("Identifier", "ResourceLocation")
    }

    // Pre-26 vanilla GuiGraphics already exposes the method set the screens are
    // written against, so the 26.x wrapper collapses to the vanilla type
    replacements.string(current.parsed < "26") {
        replace(
            "me.almana.logisticsnetworks.client.GuiGraphics",
            "net.minecraft.client.gui.GuiGraphics")
    }

    // 26.x renamed the registry lookup; root never uses the short form
    replacements.string(current.parsed < "26") {
        replace("BuiltInRegistries.ITEM.getValue(", "BuiltInRegistries.ITEM.get(")
        replace("BuiltInRegistries.FLUID.getValue(", "BuiltInRegistries.FLUID.get(")
    }

    // 26.x split the client half of PacketDistributor into its own class
    replacements.string(current.parsed < "26") {
        replace(
            "net.neoforged.neoforge.client.network.ClientPacketDistributor",
            "me.almana.logisticsnetworks.network.dist.ClientPacketDistributor")
    }

    // 1.20.1 predates both the vanilla payload/codec API and NeoForge, so it
    // routes them through the hand-rolled compat layer under network/
    replacements.string(loader == "forge") {
        replace(
            "net.minecraft.network.protocol.common.custom.CustomPacketPayload",
            "me.almana.logisticsnetworks.network.payload.CustomPacketPayload")
        replace(
            "net.neoforged.neoforge.network.handling.IPayloadContext",
            "me.almana.logisticsnetworks.network.payload.IPayloadContext")
        replace(
            "net.minecraft.network.codec.StreamCodec",
            "me.almana.logisticsnetworks.network.codec.StreamCodec")
        replace(
            "net.minecraft.network.codec.ByteBufCodecs",
            "me.almana.logisticsnetworks.network.codec.ByteBufCodecs")
        replace(
            "net.minecraft.network.RegistryFriendlyByteBuf",
            "me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf")
        replace(
            "net.neoforged.neoforge.network.PacketDistributor",
            "me.almana.logisticsnetworks.network.dist.PacketDistributor")

        // loader packages, identical types either side
        replace("net.neoforged.neoforge.server.ServerLifecycleHooks", "net.minecraftforge.server.ServerLifecycleHooks")
        replace("net.neoforged.bus.api.SubscribeEvent", "net.minecraftforge.eventbus.api.SubscribeEvent")
        replace("net.neoforged.api.distmarker.Dist", "net.minecraftforge.api.distmarker.Dist")
        replace("net.neoforged.fml.ModList", "net.minecraftforge.fml.ModList")
        replace("net.neoforged.fml.loading.FMLPaths", "net.minecraftforge.fml.loading.FMLPaths")
        replace("net.neoforged.fml.loading.FMLEnvironment", "net.minecraftforge.fml.loading.FMLEnvironment")
        replace(
            "net.neoforged.fml.common.EventBusSubscriber",
            "net.minecraftforge.fml.common.Mod.EventBusSubscriber")
    }
}

stonecutter tasks {
    order("createMinecraftArtifacts")
}

val targets = listOf("1.20.1-forge", "1.21.1-neoforge", "26.1.2-neoforge")

val collectJars by tasks.registering(Copy::class) {
    dependsOn(targets.map { ":$it:build" })
    targets.forEach { target ->
        from(layout.projectDirectory.dir("versions/$target/build/libs")) {
            include("logisticsnetworks-$target-2.0.0.jar")
        }
    }
    into(layout.buildDirectory.dir("stonecutter-jars"))
}

tasks.register("buildAll") {
    group = "build"
    dependsOn(collectJars)
}

tasks.register("dataAll") {
    group = "build"
    dependsOn(
        ":1.20.1-forge:runData",
        ":1.21.1-neoforge:runData",
        ":26.1.2-neoforge:runClientData",
        ":26.1.2-neoforge:runServerData"
    )
}
