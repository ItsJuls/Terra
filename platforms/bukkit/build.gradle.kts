plugins {
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper") version Versions.Bukkit.runPaper
}

dependencies {
    // Required for :platforms:bukkit:runDevBundleServer task
    paperweight.paperDevBundle(Versions.Bukkit.paperDevBundle)

    shaded(project(":platforms:bukkit:common"))
    // reobfArtifactConfiguration alone doesn't resolve this: it picks the module's *primary*
    // artifact, but paperweight still registers "reobf" and "runtimeElements" as separate
    // consumable configurations, and the "shaded" configuration here doesn't carry attributes
    // to disambiguate between them via variant-aware matching. Target "runtimeElements" by name
    // instead of "reobf" -- as of Minecraft 26.1, Paper dropped its internal remapper and the
    // dev bundle no longer ships reobf mappings at all, so the "reobf" variant (and its backing
    // reobfJar task) is gone/broken. ":nms" already sets MOJANG_PRODUCTION, so "runtimeElements"
    // is the Mojang-mapped jar that's actually meant to be consumed here.
    shaded(project(":platforms:bukkit:nms", configuration = "runtimeElements"))
    shaded("xyz.jpenilla", "reflection-remapper", Versions.Bukkit.reflectionRemapper)
}

tasks {
    shadowJar {
        relocate("io.papermc.lib", "com.dfsek.terra.lib.paperlib")
        relocate("com.google.common", "com.dfsek.terra.lib.google.common")
        relocate("org.apache.logging.slf4j", "com.dfsek.terra.lib.slf4j-over-log4j")
        exclude("org/slf4j/**")
        exclude("org/checkerframework/**")
        exclude("org/jetbrains/annotations/**")
        exclude("org/intellij/**")
        exclude("com/google/errorprone/**")
        exclude("com/google/j2objc/**")
        exclude("javax/**")
    }

    runServer {
        minecraftVersion(Versions.Bukkit.minecraft)
        dependsOn(shadowJar)
        pluginJars(shadowJar.get().archiveFile)

        downloadPlugins {
            modrinth("viaversion", "5.5.0")
            modrinth("viabackwards", "5.5.0")
        }
    }
}


addonDir(project.file("./run/plugins/Terra/addons"), tasks.named("runServer").get())
