plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-neoforge" /* [SC] DO NOT EDIT */

stonecutter parameters {
    constants.match(node.metadata.project.substringAfterLast('-'), "forge", "neoforge")
}
