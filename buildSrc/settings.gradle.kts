plugins {
    id("io.github.ben-manes.versions") version "0.67.0"
    id("org.zaproxy.common.settings")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
