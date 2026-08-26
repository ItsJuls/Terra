preRelease(true)

versionProjects(":common:api", version("7.0.0"))
versionProjects(":common:implementation", version("7.0.0"))
versionProjects(":platforms", version("7.0.0"))


allprojects {
    group = "com.dfsek.terra"

    configureCompilation()
    configureDependencies()
    configurePublishing()

    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.isIncremental = true
        options.release.set(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        maxHeapSize = "2G"
        ignoreFailures = false
        failFast = true
        maxParallelForks = (Runtime.getRuntime().availableProcessors() - 1).takeIf { it > 0 } ?: 1

        reports.html.required.set(false)
        reports.junitXml.required.set(false)
    }

    tasks.withType<Copy>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

afterEvaluate {
    forImmediateSubProjects(":platforms") {
        configureDistribution()
    }
    project(":platforms:bukkit:common").configureDistribution()
    project(":platforms:minestom:example").configureDistribution()
    forSubProjects(":common:addons") {
        apply(plugin = "com.gradleup.shadow")

        // Shadow (com.gradleup.shadow) now wires shadowJar into the assemble/build lifecycle
        // itself. The old finalizedBy(shadowJar) here created a *second*, opposite-direction edge
        // ("shadowJar must run after build") which, combined with Shadow's own
        // assemble-dependsOn-shadowJar wiring and the standard build-dependsOn-assemble wiring,
        // formed a genuine cycle: assemble -> shadowJar -> (after) build -> assemble.
        // dependsOn is one-directional (build requires shadowJar, no reverse ordering constraint)
        // and matches the working pattern already used in DistributionConfig.kt.
        tasks.named("build") {
            dependsOn(tasks.named("shadowJar"))
        }

        dependencies {
            "compileOnly"(project(":common:api"))
            "testImplementation"(project(":common:api"))
        }
    }
}
