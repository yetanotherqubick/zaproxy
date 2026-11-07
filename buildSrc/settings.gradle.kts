import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.github.ben-manes:gradle-versions-plugin:0.67.0")
    }
}

apply(plugin = "io.github.ben-manes.versions")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
