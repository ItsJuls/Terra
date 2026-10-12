version = version("1.3.0")

dependencies {
    api("commons-io", "commons-io", Versions.Libraries.Internal.apacheIO)
    api("com.googlecode.json-simple:json-simple:1.1.1")
    compileOnlyApi(project(":common:addons:manifest-addon-loader"))
}