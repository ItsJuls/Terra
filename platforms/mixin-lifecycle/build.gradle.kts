plugins {
    id("net.fabricmc.fabric-loom") version Versions.Mod.fabricLoom
}

javaRelease(25)

dependencies {
    shadedApi(project(":common:implementation:base"))

    compileOnly("net.fabricmc:sponge-mixin:${Versions.Mod.mixin}")
    compileOnly("io.github.llamalad7:mixinextras-common:${Versions.Mod.mixinExtras}")

    implementation(project(":platforms:mixin-common"))

    minecraft("com.mojang:minecraft:${Versions.Mod.minecraft}")

    implementation("org.incendo", "cloud-fabric", Versions.Fabric.cloud) {
        exclude("net.fabricmc")
        exclude("net.fabricmc.fabric-api")
        exclude("me.lucko", "fabric-permissions-api")
    }
}

loom {
    accessWidenerPath.set(project(":platforms:mixin-common").file("src/main/resources/terra.accesswidener"))
}
