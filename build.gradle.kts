plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev") version "2.0.140"
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
val parchment_minecraft_version: String by project
val parchment_mappings_version: String by project
val mekanism_file_id: String by project
val ars_nouveau_file_id: String by project
val arseng_file_id: String by project
val ae2_version: String by project
val ae2_file_id: String by project
val refined_storage_version: String by project
val refined_storage_file_id: String by project
val cable_tiers_file_id: String by project
val ftb_teams_file_id: String by project
val emi_file_id: String by project
val guideme_file_id: String by project
val sophisticated_core_file_id: String by project
val functional_storage_file_id: String by project
val create_file_id: String by project
val building_gadgets_file_id: String by project
val appmek_file_id: String by project
val rs_mekanism_file_id: String by project
val cc_version: String by project
val create_runtime = providers.gradleProperty("create_runtime").orElse("true").map { it.toBoolean() }
val ae2Runtime = providers.gradleProperty("ae2_runtime").orElse("true").map { it.toBoolean() }
val refinedStorageRuntime = providers.gradleProperty("refined_storage_runtime").orElse("true").map { it.toBoolean() }
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
    maven("https://cursemaven.com") {
        content { includeGroup("curse.maven") }
    }
}

base {
    archivesName.set(mod_id)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory.set(file("run/server"))
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }
        create("data") {
            data()
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
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

    unitTest {
        testedMod.set(mods.named(mod_id))
        enable()
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }
configurations.testRuntimeClasspath { extendsFrom(localRuntime) }
val createJar: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(createJar) }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    createJar("curse.maven:create-328085:${create_file_id}")
    // Ponder only ships inside Create
    compileOnly(files(provider { zipTree(createJar.singleFile).matching { include("META-INF/jarjar/ponder-*.jar") } }))
    if (create_runtime.get()) {
        localRuntime("curse.maven:create-328085:${create_file_id}")
    }

    compileOnly("curse.maven:building-gadgets-298187:${building_gadgets_file_id}")
    if (buildingGadgetsRuntime.get()) {
        localRuntime("curse.maven:building-gadgets-298187:${building_gadgets_file_id}")
    }

    compileOnly("curse.maven:jei-238222:${jei_file_id}")
    localRuntime("curse.maven:jei-238222:${jei_file_id}")

    compileOnly("curse.maven:mekanism-268560:${mekanism_file_id}")

    compileOnly("curse.maven:ars-nouveau-401955:${ars_nouveau_file_id}")
    compileOnly("curse.maven:ars-energistique-905641:${arseng_file_id}")

    compileOnly("curse.maven:applied-energistics-2-223794:${ae2_file_id}")
    if (ae2Runtime.get()) {
        localRuntime("curse.maven:applied-energistics-2-223794:${ae2_file_id}")
    }

    compileOnly("curse.maven:refined-storage-243076:${refined_storage_file_id}")
    if (refinedStorageRuntime.get()) {
        localRuntime("curse.maven:refined-storage-243076:${refined_storage_file_id}")
    }

    compileOnly("curse.maven:applied-mekanistics-574300:${appmek_file_id}")
    compileOnly("curse.maven:refined-storage-mekanism-integration-1230504:${rs_mekanism_file_id}")

    compileOnly("curse.maven:cable-tiers-454382:${cable_tiers_file_id}")

    compileOnly("cc.tweaked:cc-tweaked-${minecraft_version}-forge-api:${cc_version}")
    if (ccRuntime.get()) {
        localRuntime("cc.tweaked:cc-tweaked-${minecraft_version}-forge:${cc_version}")
    }

    compileOnly("curse.maven:guideme-1173950:${guideme_file_id}")
    localRuntime("curse.maven:guideme-1173950:${guideme_file_id}")

    compileOnly("curse.maven:ftb-teams-404468:${ftb_teams_file_id}")

    compileOnly("curse.maven:emi-580555:${emi_file_id}")

    compileOnly("curse.maven:jade-324717:${jade_file_id}")
    localRuntime("curse.maven:jade-324717:${jade_file_id}")

    compileOnly("curse.maven:iris-455508:${iris_file_id}")

    compileOnly("curse.maven:sophisticated-core-618298:${sophisticated_core_file_id}")
    compileOnly("curse.maven:functional-storage-556861:${functional_storage_file_id}")
    testRuntimeOnly("curse.maven:sophisticated-core-618298:${sophisticated_core_file_id}")
}

tasks.test {
    useJUnitPlatform()
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

val copyJarDest = File("C:/Users/Kanishq/curseforge/minecraft/Instances/Test/mods")
if (copyJarDest.isDirectory) {
    tasks.register<Copy>("copyJar") {
        from(tasks.named("jar"))
        into(copyJarDest)
    }

    tasks.named("build") {
        finalizedBy("copyJar")
    }
}
