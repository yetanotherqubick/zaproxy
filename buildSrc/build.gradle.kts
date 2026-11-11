import com.diffplug.spotless.LineEnding

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = LineEnding.UNIX

    kotlinGradle {
        ktlint()
    }

    java {
        licenseHeaderFile(file("../gradle/spotless/license.java"))
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs = listOf("-Xlint:all", "-Werror", "-parameters")
    if (JavaVersion.current().getMajorVersion() >= "21") {
        options.compilerArgs = options.compilerArgs + "-Xlint:-this-escape"
    }
}

dependencies {
    // Libraries used directly by the build logic.
    implementation(libs.commons.configuration)
    implementation(libs.commons.configuration2)
    implementation(libs.commons.io)
    implementation(libs.github.api)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.javaparser.core)
    implementation(libs.javaSemver)
    implementation(libs.jgit.core)

    // Gradle plugins and build tools used by the convention plugins.
    implementation(libs.cyclonedx.gradle.plugin)
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.gradle.download.task)
    implementation(libs.install4j.gradle)
    implementation(libs.japicmp.gradle.plugin)
    implementation(libs.launch4j)
    implementation(libs.spotless.plugin.gradle)

    // Annotation types needed only at compile time.
    compileOnly(libs.bridge.method.annotation) {
        exclude(group = "org.jenkins-ci")
    }
    compileOnly(libs.findbugsAnnotations)
}
