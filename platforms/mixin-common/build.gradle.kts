plugins {
    id("net.fabricmc.fabric-loom") version Versions.Mod.fabricLoom
}

javaRelease(25)

loom {
    accessWidenerPath.set(file("src/main/resources/terra.accesswidener"))
}

dependencies {
    shadedApi(project(":common:implementation:base"))

    compileOnly("net.fabricmc:sponge-mixin:${Versions.Mod.mixin}")

    minecraft("com.mojang:minecraft:${Versions.Mod.minecraft}")
}
