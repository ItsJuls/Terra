rootProject.name = "Terra"

// Seismic: the Eyjafjalla branch of ItsJuls/Seismic, fetched from GitHub automatically.
// Gradle keeps a clone in .gradle/seismic-src (git-ignored) and updates it on every build when online,
// then uses it for EVERY request of com.dfsek:seismic (also the old version paralithic asks for).
// Needs git on the PATH. Change the branch here; delete .gradle/seismic-src to force a fresh clone.
val seismicRepo = "https://github.com/ItsJuls/Seismic.git"
val seismicBranch = "Eyjafjalla"
val seismicDir = file(".gradle/seismic-src")

fun runGit(dir: File, vararg args: String): Boolean = try {
    ProcessBuilder(listOf("git") + args).directory(dir).inheritIO().start().waitFor() == 0
} catch (e: Exception) {
    false
}

if (!File(seismicDir, ".git").exists()) {
    seismicDir.parentFile.mkdirs()
    if (!runGit(rootDir, "clone", "--branch", seismicBranch, seismicRepo, seismicDir.path))
        throw GradleException("Could not clone $seismicRepo ($seismicBranch) into $seismicDir - is git installed and are you online?")
} else if (runGit(seismicDir, "fetch", "--tags", "origin", seismicBranch)) {
    runGit(seismicDir, "checkout", "-q", seismicBranch)
    runGit(seismicDir, "reset", "-q", "--hard", "origin/$seismicBranch")
} else {
    logger.warn("Seismic: could not update $seismicDir (offline?), building the copy that is already there")
}

includeBuild(seismicDir) {
    dependencySubstitution {
        substitute(module("com.dfsek:seismic")).using(project(":"))
    }
}


fun includeImmediateChildren(dir: File, type: String) {
    dir.walkTopDown().maxDepth(1).forEach {
        if (!it.isDirectory || !File(it, "build.gradle.kts").exists()) return@forEach
        val addonDir = it.relativeTo(file(".")).path.replace("/", ":").replace("\\", ":")
        logger.info("Including $type directory \"$addonDir\" as subproject.")
        include(addonDir)
    }
}

includeImmediateChildren(file("common/api"), "API")

includeImmediateChildren(file("common/implementation"), "implementation")

includeImmediateChildren(file("common/addons"), "addon")

includeImmediateChildren(file("platforms"), "platform")

includeImmediateChildren(file("platforms/bukkit/nms"), "Bukkit NMS")

include(":platforms:bukkit:common")
include(":platforms:minestom:example")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.solo-studios.ca/releases") {
            name = "Solo Studios"
        }
        maven("https://maven.solo-studios.ca/snapshots") {
            name = "Solo Studios"
        }
        maven("https://maven.fabricmc.net") {
            name = "Fabric Maven"
        }
        maven("https://maven.architectury.dev/") {
            name = "Architectury Maven"
        }
        maven("https://files.minecraftforge.net/maven/") {
            name = "Forge Maven"
        }
        maven("https://maven.quiltmc.org/repository/release/") {
            name = "Quilt"
        }
    }
}

// settings.gradle.kts
val isCiServer = System.getenv().containsKey("CI")
// Cache build artifacts, so expensive operations do not need to be re-computed
buildCache {
    local {
        isEnabled = !isCiServer
    }
}