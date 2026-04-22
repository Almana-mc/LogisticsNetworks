import net.minecraftforge.gradle.common.util.ModConfig
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
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

plugins {
    id("eclipse")
    id("idea")
    id("maven-publish")
    id("net.minecraftforge.gradle") version "[6.0.24,6.2)"
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

    val runsContainer = runs as org.gradle.api.NamedDomainObjectContainer<RunConfig>
    runsContainer.configureEach {
        workingDirectory(project.file("run"))
        property("forge.logging.markers", "REGISTRIES")
        property("forge.logging.console.level", "debug")

        val modsContainer = mods as org.gradle.api.NamedDomainObjectContainer<ModConfig>
        modsContainer.maybeCreate(modId).source(mainSourceSet)
    }

    runsContainer.maybeCreate("client").apply {
        property("forge.enabledGameTestNamespaces", modId)
    }
    runsContainer.maybeCreate("server").apply {
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

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = uri("https://maven.blamejared.com"))
    maven(url = uri("https://modmaven.dev/"))
    maven(url = uri("https://api.modrinth.com/maven"))
    maven(url = uri("https://maven.ftb.dev/releases"))
}

dependencies {
    add("minecraft", "net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")

    compileOnly("mezz.jei:jei-${minecraftVersion}-common-api:${jeiVersion}")
    compileOnly("mezz.jei:jei-${minecraftVersion}-forge-api:${jeiVersion}")
    runtimeOnly("mezz.jei:jei-${minecraftVersion}-forge:${jeiVersion}")

    compileOnly("mekanism:Mekanism:${mekanismVersion}")
    compileOnly("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraftVersion}:${arsNouveauVersion}")
    compileOnly("maven.modrinth:jade:${jadeVersion}")
    compileOnly("appeng:appliedenergistics2-forge:${ae2Version}")
    compileOnly("org.appliedenergistics:guideme:${guidemeVersion}:api")
    runtimeOnly("org.appliedenergistics:guideme:${guidemeVersion}")
    compileOnly("dev.ftb.mods:ftb-teams-forge:${ftbTeamsVersion}") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
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
    from(tasks.named("jar").map { it.outputs.files })
    into(modsDir)
    isEnabled = modsDir.exists()
}

tasks.named("build") {
    finalizedBy("copyJar")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("file://${project.projectDir}/repo")
        }
    }
}
