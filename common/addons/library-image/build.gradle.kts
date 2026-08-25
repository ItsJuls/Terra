version = version("1.1.0")

dependencies {
    compileOnlyApi(project(":common:addons:manifest-addon-loader"))
    implementation("io.github.blackears:svg-salamander:1.1.5.5")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    api("com.googlecode.json-simple:json-simple:1.1.1")
}
