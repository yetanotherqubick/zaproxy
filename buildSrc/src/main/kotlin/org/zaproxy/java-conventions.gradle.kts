package org.zaproxy

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.diffplug.spotless")
    id("org.zaproxy.common")
    id("net.ltgt.errorprone")
}

val versionCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

spotless {
    kotlinGradle {
        ktlint()
    }
}

plugins.withType(JavaPlugin::class.java) {
    // Error Prone is a compiler tool, not an application dependency.
    dependencies.add("errorprone", versionCatalog.findLibrary("errorprone-core").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks.set(true)
        error(
            "MissingOverride",
            "WildcardImport",
        )
    }
}
