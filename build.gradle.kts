plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev") version "2.0.146"
}

val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version: String by project
val neo_version_range: String by project
val loader_version_range: String by project
val mod_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_authors: String by project
val mod_description: String by project
val jei_file_id: String by project
val jade_file_id: String by project
val iris_file_id: String by project
val mekanism_version: String by project
val ars_nouveau_version: String by project
val ae2_version: String by project
val ae2_file_id: String by project
val refined_storage_version: String by project
val refined_storage_file_id: String by project
val ftb_teams_file_id: String by project
val ftb_library_file_id: String by project
val emi_version: String by project
val guideme_file_id: String by project
val sophisticated_core_file_id: String by project
val building_gadgets_file_id: String by project
val cc_version: String by project
val ae2Runtime = providers.gradleProperty("ae2_runtime").orElse("true").map { it.toBoolean() }
val extendedAeRuntime = providers.gradleProperty("extendedae_runtime").orElse("true").map { it.toBoolean() }
val refinedStorageRuntime = providers.gradleProperty("refined_storage_runtime").orElse("true").map { it.toBoolean() }
val cableTiersRuntime = providers.gradleProperty("cable_tiers_runtime").orElse("true").map { it.toBoolean() }
val buildingGadgetsRuntime = providers.gradleProperty("building_gadgets_runtime").orElse("true").map { it.toBoolean() }
val ccRuntime = providers.gradleProperty("cc_runtime").orElse("true").map { it.toBoolean() }

version = "${minecraft_version}-${mod_version}"
group = mod_group_id

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.squiddev.cc") {
        content { includeGroup("cc.tweaked") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://cursemaven.com") {
        content { includeGroup("curse.maven") }
    }
    // Enable when 26.1.2 is supported.
    /*
    maven("https://maven.createmod.net")
    */
}

base {
    archivesName.set(mod_id)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

neoForge {
    version = neo_version

    runs {
        create("client") {
            client()
            gameDirectory = file("run/${minecraft_version}/client")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("server") {
            server()
            gameDirectory = file("run/${minecraft_version}/server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("data") {
            clientData()
            gameDirectory = file("run/${minecraft_version}/data")
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/client/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
        create("serverData") {
            serverData()
            gameDirectory = file("run/${minecraft_version}/serverData")
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/server/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }

}

sourceSets.main.get().resources.srcDir("src/generated/client")
sourceSets.main.get().resources.srcDir("src/generated/server")

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }

dependencies {
    compileOnly("curse.maven:sophisticated-core-618298:${sophisticated_core_file_id}") { isTransitive = false }
    compileOnly("curse.maven:jei-238222:${jei_file_id}")
    localRuntime("curse.maven:jei-238222:${jei_file_id}")

    compileOnly("curse.maven:ftb-teams-404468:${ftb_teams_file_id}") {
        isTransitive = false
    }
    compileOnly("curse.maven:ftb-library-404465:${ftb_library_file_id}") {
        isTransitive = false
    }

    compileOnly("curse.maven:guideme-1173950:${guideme_file_id}")
    localRuntime("curse.maven:guideme-1173950:${guideme_file_id}")

    compileOnly("curse.maven:jade-324717:${jade_file_id}")
    localRuntime("curse.maven:jade-324717:${jade_file_id}")

    compileOnly("curse.maven:applied-energistics-2-223794:${ae2_file_id}")
    if (ae2Runtime.get()) {
        localRuntime("curse.maven:applied-energistics-2-223794:${ae2_file_id}")
    }
    if (extendedAeRuntime.get()) {
        localRuntime("curse.maven:ex-pattern-provider-892005:9453241")
        localRuntime("maven.modrinth:glodium:26.1-1.2-neoforge")
    }

    compileOnly("curse.maven:refined-storage-243076:${refined_storage_file_id}")
    if (refinedStorageRuntime.get()) {
        localRuntime("curse.maven:refined-storage-243076:${refined_storage_file_id}")
    }

    compileOnly("curse.maven:cable-tiers-454382:9115770") {
        isTransitive = false
    }
    if (cableTiersRuntime.get() && refinedStorageRuntime.get()) {
        localRuntime("curse.maven:cable-tiers-454382:9115770") {
            isTransitive = false
        }
    }

    compileOnly("cc.tweaked:cc-tweaked-${minecraft_version}-forge-api:${cc_version}")
    if (ccRuntime.get()) {
        localRuntime("cc.tweaked:cc-tweaked-${minecraft_version}-forge:${cc_version}")
    }

    compileOnly("curse.maven:building-gadgets-298187:${building_gadgets_file_id}")
    if (buildingGadgetsRuntime.get()) {
        localRuntime("curse.maven:building-gadgets-298187:${building_gadgets_file_id}")
    }

    // Iris API — compile-only; shaders are an optional runtime dependency.
    compileOnly("curse.maven:iris-455508:${iris_file_id}")

    // Enable when 26.1.2 is supported.
    /*
    compileOnly("com.simibubi.create:create-${minecraft_version}:${create_version}") {
        isTransitive = false
    }
    compileOnly("net.createmod.ponder:ponder-neoforge:${ponder_version}") {
        isTransitive = false
    }

    compileOnly("mekanism:Mekanism:${mekanism_version}")

    compileOnly("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraft_version}:${ars_nouveau_version}")

    compileOnly("dev.emi:emi-neoforge:${emi_version}") {
        isTransitive = false
    }
    */
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to neo_version,
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
        "ae2_version" to ae2_version,
        "refined_storage_version" to refined_storage_version
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = mod_id
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "almana"
            val channel = if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
            url = uri("https://maven.almanax21.com/$channel")
            credentials(PasswordCredentials::class)
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

val copyJarDest = File("C:/Users/Kanishq/AppData/Roaming/PrismLauncher/instances/26.1/minecraft/mods")
if (copyJarDest.isDirectory) {
    tasks.register<Copy>("copyJar") {
        from(tasks.named("jar"))
        into(copyJarDest)
    }

    tasks.named("build") {
        finalizedBy("copyJar")
    }
}
