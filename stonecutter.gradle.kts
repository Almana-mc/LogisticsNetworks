plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-neoforge" /* [SC] DO NOT EDIT */

stonecutter parameters {
    constants.match(node.metadata.project.substringAfterLast('-'), "forge", "neoforge")
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
