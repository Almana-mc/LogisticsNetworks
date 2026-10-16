import net.minecraftforge.gradle.common.util.ModConfig
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.jarjar.JarJarProjectExtension
import org.spongepowered.asm.gradle.plugins.MixinExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import java.util.Properties

plugins {
    id("eclipse")
    id("idea")
    id("maven-publish")
    id("net.minecraftforge.gradle") version "[6.0.24,6.2)"
    id("org.spongepowered.mixin") version "0.7.38"
}

val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val forgeVersion = property("forge_version") as String
val forgeVersionRange = property("forge_version_range") as String
val loaderVersionRange = property("loader_version_range") as String
val mappingChannelProp = property("mapping_channel") as String
val mappingVersionProp = property("mapping_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modVersion = property("mod_version") as String
val modGroupId = property("mod_group_id") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val jeiVersion = property("jei_version") as String
val mekanismVersion = property("mekanism_version") as String
val arsNouveauVersion = property("ars_nouveau_version") as String
val jadeVersion = property("jade_version") as String
val ae2Version = property("ae2_version") as String
val ftbTeamsVersion = property("ftb_teams_version") as String
val guidemeVersion = property("guideme_version") as String

version = "${minecraftVersion}-${modVersion}"
group = modGroupId

base {
    archivesName.set(modId)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val mainSourceSet = the<SourceSetContainer>().named("main").get()

extensions.configure<UserDevExtension>("minecraft") {
    mappings(mappingChannelProp, mappingVersionProp)
    copyIdeResources.set(true)
    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    val runsContainer = runs as org.gradle.api.NamedDomainObjectContainer<RunConfig>
    runsContainer.configureEach {
        workingDirectory(project.file("run/1.20.1"))
        property("forge.logging.markers", "REGISTRIES")
        property("mixin.env.remapRefMap", "true")
        property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
        property("forge.logging.console.level", "debug")

        val modsContainer = mods as org.gradle.api.NamedDomainObjectContainer<ModConfig>
        modsContainer.maybeCreate(modId).source(mainSourceSet)
    }

    runsContainer.maybeCreate("client").apply {
        property("forge.enabledGameTestNamespaces", modId)
    }
    runsContainer.maybeCreate("server").apply {
        workingDirectory(project.file("run/1.20.1-server"))
        property("forge.enabledGameTestNamespaces", modId)
        args("--nogui")
    }
    runsContainer.maybeCreate("gameTestServer").apply {
        property("forge.enabledGameTestNamespaces", modId)
    }
    runsContainer.maybeCreate("data").apply {
        workingDirectory(project.file("run-data"))
        args(
            "--mod", modId,
            "--all",
            "--output", file("src/generated/resources/").absolutePath,
            "--existing", file("src/main/resources/").absolutePath
        )
    }
}

the<SourceSetContainer>().named("main") {
    resources.srcDir("src/generated/resources")
}

extensions.configure<MixinExtension>("mixin") {
    add(mainSourceSet, "${modId}.refmap.json")
    config("${modId}.functionalstorage.mixins.json")
}

val jarJar = extensions.getByType<JarJarProjectExtension>()
jarJar.enable()

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = uri("https://maven.blamejared.com"))
    maven(url = uri("https://modmaven.dev/"))
    maven(url = uri("https://api.modrinth.com/maven"))
    maven(url = uri("https://maven.ftb.dev/releases"))
}

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }
val fg = extensions.getByType<net.minecraftforge.gradle.userdev.DependencyManagementExtension>()
val testMods = Properties().apply {
    val file = project.file("run/1.20.1/testmods.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun testMod(name: String) = testMods.getProperty(name, "false").toBoolean()

dependencies {
    add("minecraft", "net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)
    localRuntime("io.github.llamalad7:mixinextras-forge:0.4.1")
    add("jarJar", "io.github.llamalad7:mixinextras-forge:0.4.1") {
        jarJar.ranged(this, "[0.4.1,)")
    }

    compileOnly("mezz.jei:jei-${minecraftVersion}-common-api:${jeiVersion}")
    compileOnly("mezz.jei:jei-${minecraftVersion}-forge-api:${jeiVersion}")
    localRuntime(fg.deobf("mezz.jei:jei-${minecraftVersion}-forge:${jeiVersion}"))

    compileOnly("mekanism:Mekanism:${mekanismVersion}")
    compileOnly("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraftVersion}:${arsNouveauVersion}")
    compileOnly("maven.modrinth:jade:${jadeVersion}")
    compileOnly("appeng:appliedenergistics2-forge:${ae2Version}")
    compileOnly("org.appliedenergistics:guideme:${guidemeVersion}:api")
    localRuntime(fg.deobf("org.appliedenergistics:guideme:${guidemeVersion}"))
    compileOnly("dev.ftb.mods:ftb-teams-forge:${ftbTeamsVersion}") { isTransitive = false }
    compileOnly("maven.modrinth:oculus:1.20.1-1.8.0") { isTransitive = false }
    compileOnly("maven.modrinth:functional-storage:1.20.1-1.2.14") { isTransitive = false }
    compileOnly("maven.modrinth:sophisticated-core:1.20.1-1.5.10.2402") { isTransitive = false }

    // Local test mods (testmods.properties)
    if (testMod("mek")) localRuntime(fg.deobf("mekanism:Mekanism:${mekanismVersion}"))
    if (testMod("ars")) {
        localRuntime(fg.deobf("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraftVersion}:${arsNouveauVersion}"))
        localRuntime(fg.deobf("maven.modrinth:curios:5.14.1+1.20.1"))
        localRuntime(fg.deobf("maven.modrinth:geckolib:aC5KMoNg")) // geckolib-forge 4.8.4
        localRuntime("io.github.llamalad7:mixinextras-forge:0.5.4")
    }
    if (testMod("ftbteams")) {
        localRuntime(fg.deobf("dev.ftb.mods:ftb-teams-forge:${ftbTeamsVersion}", closureOf<ModuleDependency> { isTransitive = false }))
        localRuntime(fg.deobf("dev.ftb.mods:ftb-library-forge:2001.2.13", closureOf<ModuleDependency> { isTransitive = false }))
        localRuntime(fg.deobf("maven.modrinth:architectury-api:9.2.14+forge"))
    }
    if (testMod("jade")) localRuntime(fg.deobf("maven.modrinth:jade:${jadeVersion}"))
    if (testMod("xaero")) localRuntime(fg.deobf("maven.modrinth:xaeros-minimap:forge-1.20.1-26.6.0"))
    if (testMod("ae2")) localRuntime(fg.deobf("appeng:appliedenergistics2-forge:${ae2Version}"))
    if (testMod("soph")) {
        localRuntime(fg.deobf("maven.modrinth:sophisticated-core:1.20.1-1.5.10.2402"))
        localRuntime(fg.deobf("maven.modrinth:sophisticated-backpacks:1.20.1-3.26.12.2206"))
    }
    if (testMod("oculus")) {
        localRuntime(fg.deobf("maven.modrinth:oculus:1.20.1-1.8.0"))
        localRuntime(fg.deobf("maven.modrinth:embeddium:0.3.31+mc1.20.1"))
    }
    if (testMod("fs")) {
        localRuntime(fg.deobf("maven.modrinth:functional-storage:1.20.1-1.2.14"))
        localRuntime(fg.deobf("maven.modrinth:titanium:1.20.1-3.8.35"))
    }
    if (testMod("spark")) localRuntime(fg.deobf("maven.modrinth:spark:1.10.53-forge"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("slim")
    destinationDirectory.set(layout.buildDirectory.dir("devlibs"))
}

tasks.named<Jar>("jarJar") {
    archiveClassifier.set("")
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "forge_version" to forgeVersion,
        "forge_version_range" to forgeVersionRange,
        "loader_version_range" to loaderVersionRange,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "mod_authors" to modAuthors,
        "mod_description" to modDescription
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
}

tasks.register<Copy>("copyJar") {
    val modsPath = "C:/Users/Kanishq/AppData/Roaming/PrismLauncher/instances/1.20.1-LN/minecraft/mods"
    val modsDir = File(modsPath)
    from(tasks.named("jarJar").map { it.outputs.files })
    into(modsDir)
    isEnabled = modsDir.exists()
}

tasks.named("build") {
    finalizedBy("copyJar")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = modId
            artifact(tasks.named<Jar>("jarJar")) {
                builtBy(provider { tasks.getByName("reobfJarJar") })
            }
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
