javaRelease(25)

repositories {
    maven("https://repo.nexomc.com/releases") {
        name = "Nexo"
    }
    maven("https://repo.momirealms.net/releases") {
        name = "MomiRealms"
    }
}

dependencies {
    shadedApi(project(":common:implementation:base"))

    compileOnly("io.papermc.paper", "paper-api", Versions.Bukkit.paper)

    compileOnly("org.mvplugins.multiverse.core", "multiverse-core", Versions.Bukkit.multiverse)

    // Optional integrations, only called when the plugins are installed.
    compileOnly("com.nexomc:nexo:${Versions.Bukkit.nexo}") { isTransitive = false }
    compileOnly("net.momirealms:custom-crops:${Versions.Bukkit.customCrops}") { isTransitive = false }

    shadedApi("io.papermc", "paperlib", Versions.Bukkit.paperLib)

    shadedApi("com.google.guava", "guava", Versions.Libraries.Internal.guava)

    shadedApi("org.incendo", "cloud-paper", Versions.Bukkit.cloud)
}
