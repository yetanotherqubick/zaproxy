package org.zaproxy

import com.diffplug.spotless.LineEnding
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.zaproxy.gradle.common.spotless.FormatPropertiesStep

plugins {
    id("com.diffplug.spotless")
}

spotless {
    lineEndings = LineEnding.UNIX

    format("properties") {
        target("**/*.properties")
        targetExclude(
            "**/Messages_*_*.properties",
            "**/gradle.properties",
        )
        addStep(FormatPropertiesStep.create())
    }
}

plugins.withType(JavaPlugin::class.java) {
    tasks.withType(JavaCompile::class.java).configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs = listOf("-Xlint:all", "-Werror", "-parameters")
    }
}
