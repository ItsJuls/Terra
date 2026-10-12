import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.configuration.IncludeConfigurations

plugins {
    id("net.fabricmc.fabric-loom") version Versions.Mod.fabricLoom
}

javaRelease(25)

dependencies {
    shadedApi(project(":common:implementation:base"))

    implementation(project(":platforms:mixin-common"))
    shaded(project(":platforms:mixin-common")) { isTransitive = false }
    implementation(project(":platforms:mixin-lifecycle"))
    shaded(project(":platforms:mixin-lifecycle")) { isTransitive = false }

    minecraft("com.mojang:minecraft:${Versions.Mod.minecraft}")

    implementation("net.fabricmc:fabric-loader:${Versions.Mod.fabricLoader}")

    implementation("org.incendo", "cloud-fabric", Versions.Fabric.cloud) {
        exclude("me.lucko", "fabric-permissions-api")
    }
    include("org.incendo", "cloud-fabric", Versions.Fabric.cloud)

    runtimeOnly("net.fabricmc.fabric-api", "fabric-api", Versions.Fabric.fabricAPI)
}

loom {
    accessWidenerPath.set(project(":platforms:mixin-common").file("src/main/resources/terra.accesswidener"))

    mods {
        register("terra") {
            sourceSet(sourceSets.main.get())
            sourceSet("main", project(":platforms:mixin-common"))
            sourceSet("main", project(":platforms:mixin-lifecycle"))
        }
    }
}


addonDir(project.file("./run/config/Terra/addons"), tasks.named("configureLaunch").get())

// Minecraft 26.x is unobfuscated, so nothing is remapped anymore: the shadow jar is the deployable mod jar.
// Loom only nests `include`d jars (cloud-fabric) into the plain `jar` task, so nest them into the shadow jar too.
IncludeConfigurations.nestJars(project, tasks.named<ShadowJar>("shadowJar"), configurations.named("include"))

tasks {
    jar {
        // Un-shaded development jar; keep it from clashing with the real jar's name.
        archiveClassifier.set("dev")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("${rootProject.name}-fabric-${project.version}.jar")
    }
}
