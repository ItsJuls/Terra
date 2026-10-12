version = version("1.1.0")

dependencies {
    compileOnlyApi(project(":common:addons:manifest-addon-loader"))
    implementation("io.github.blackears:svg-salamander:1.1.5.5")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    api("com.googlecode.json-simple:json-simple:1.1.1")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // Other plugins on the server may ship their own (older) JTS. Addon class loaders ask their parent first, and on Paper
    // that includes other plugins' classes, so an unrelocated copy here can be shadowed and fail with NoSuchMethodError.
    relocate("org.locationtech.jts", "com.dfsek.terra.addons.image.lib.jts")
}
