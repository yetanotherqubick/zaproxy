buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.zaproxy.common.settings:org.zaproxy.common.settings.gradle.plugin:0.5.0")
        classpath("io.github.ben-manes:gradle-versions-plugin:0.67.0")
    }
}

apply(plugin = "org.zaproxy.common.settings")
apply(plugin = "io.github.ben-manes.versions")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
