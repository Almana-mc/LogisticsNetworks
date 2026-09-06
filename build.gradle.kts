plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev") version "2.0.141"
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
val jei_version: String by project
val jade_version: String by project
val mekanism_version: String by project
val ars_nouveau_version: String by project
val ae2_version: String by project
val ftb_teams_version: String by project
val ftb_library_version: String by project
val emi_version: String by project
val guideme_version: String by project

version = "${minecraft_version}-${mod_version}"
group = mod_group_id

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.blamejared.com")
    maven("https://modmaven.dev/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.ftb.dev/releases")
    maven("https://maven.terraformersmc.com/releases")
    // Enable when 26.1.2 is supported.
    /*
    maven("https://maven.createmod.net")
    */
    exclusiveContent {
        forRepository { maven("https://www.cursemaven.com") }
        filter { includeGroup("curse.maven") }
    }
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

dependencies {
    compileOnly("curse.maven:sophisticated-core-618298:8705495") { isTransitive = false }
    compileOnly("mezz.jei:jei-${minecraft_version}-common-api:${jei_version}")
    compileOnly("mezz.jei:jei-${minecraft_version}-neoforge-api:${jei_version}")
    runtimeOnly("mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}")

    compileOnly("dev.ftb.mods:ftb-teams-neoforge:${ftb_teams_version}") {
        isTransitive = false
    }
    compileOnly("dev.ftb.mods:ftb-library-neoforge:${ftb_library_version}") {
        isTransitive = false
    }

    compileOnly("org.appliedenergistics:guideme:${guideme_version}")
    runtimeOnly("org.appliedenergistics:guideme:${guideme_version}")

    compileOnly("maven.modrinth:jade:${jade_version}")
    runtimeOnly("maven.modrinth:jade:${jade_version}")

    compileOnly("org.appliedenergistics:appliedenergistics2:${ae2_version}")
    runtimeOnly("org.appliedenergistics:appliedenergistics2:${ae2_version}")

    // Iris API — compile-only; shaders are an optional runtime dependency.
    compileOnly("maven.modrinth:iris:1.10.9+26.1-neoforge") {
        isTransitive = false
    }

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
        "ae2_version" to ae2_version
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
