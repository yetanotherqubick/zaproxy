plugins {
    id("org.zaproxy.common.settings")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// The plugin is already on the settings classpath; avoid requesting it again through the plugins DSL.
apply(plugin = "io.github.ben-manes.versions")
