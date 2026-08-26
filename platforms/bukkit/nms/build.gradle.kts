plugins {
    id("io.papermc.paperweight.userdev")
}

// Newer paperweight-userdev publishes both a "reobf" and a "runtimeElements" variant of this
// module; without picking one, consumers that depend on this project (e.g. :platforms:bukkit)
// can't resolve which to use. MOJANG_PRODUCTION matches how this module is actually used
// elsewhere already (Reflection.java calls ReflectionRemapper.forReobfMappingsInPaperJar(),
// jpenilla's companion library specifically for Mojang-mapped-production plugins) and is
// Paper's own recommended default; switch to REOBF_PRODUCTION instead if this plugin needs
// to keep running on plain Spigot/CraftBukkit.
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    api(project(":platforms:bukkit:common"))
    paperweight.paperDevBundle(Versions.Bukkit.paperDevBundle)
    implementation("xyz.jpenilla", "reflection-remapper", Versions.Bukkit.reflectionRemapper)
}