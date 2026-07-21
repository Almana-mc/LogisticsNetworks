plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev.legacyforge") version "2.0.141"
}

val minecraft_version: String by project
val minecraft_version_range: String by project
val forge_version: String by project
val forge_version_range: String by project
val loader_version_range: String by project
val mod_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_authors: String by project
val mod_description: String by project
val jei_version: String by project
val mekanism_version: String by project
val ars_nouveau_version: String by project
val jade_version: String by project
val ae2_version: String by project
val ftb_teams_version: String by project
val guideme_version: String by project

version = mod_version
group = mod_group_id

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.blamejared.com")
    maven("https://modmaven.dev/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.ftb.dev/releases")
}

base {
    archivesName.set("${mod_id}-${minecraft_version}-forge")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

legacyForge {
    version = "${minecraft_version}-${forge_version}"

    runs {
        create("client") {
            client()
            gameDirectory = file("run/client")
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
            programArgument("--nogui")
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
        }
        create("data") {
            data()
            gameDirectory = file("run/data")
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
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

sourceSets.main.get().resources.srcDir("src/generated/resources")

val localRuntime = configurations.create("localRuntime")

configurations.named("runtimeClasspath") {
    extendsFrom(localRuntime)
}

obfuscation {
    createRemappingConfiguration(localRuntime)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "modCompileOnly"("mezz.jei:jei-${minecraft_version}-common-api:${jei_version}")
    "modCompileOnly"("mezz.jei:jei-${minecraft_version}-forge-api:${jei_version}")
    "modLocalRuntime"("mezz.jei:jei-${minecraft_version}-forge:${jei_version}")

    "modCompileOnly"("mekanism:Mekanism:${mekanism_version}")
    "modCompileOnly"("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraft_version}:${ars_nouveau_version}")
    "modCompileOnly"("maven.modrinth:jade:${jade_version}")
    "modCompileOnly"("appeng:appliedenergistics2-forge:${ae2_version}")
    "modCompileOnly"("org.appliedenergistics:guideme:${guideme_version}:api")
    "modLocalRuntime"("org.appliedenergistics:guideme:${guideme_version}")
    "modCompileOnly"("dev.ftb.mods:ftb-teams-forge:${ftb_teams_version}") {
        isTransitive = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "forge_version" to forge_version,
        "forge_version_range" to forge_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
    exclude(
        "data/logisticsnetworks/advancement/**",
        "data/logisticsnetworks/loot_table/**",
        "data/logisticsnetworks/recipe/**",
        "data/logisticsnetworks/tags/block/**",
        "data/logisticsnetworks/tags/fluid/**",
        "data/logisticsnetworks/tags/item/**"
    )
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = project.layout.projectDirectory.dir("repo").asFile.toURI()
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
