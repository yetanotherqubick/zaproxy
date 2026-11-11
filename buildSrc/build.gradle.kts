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
    implementation(libs.javaparser.core)
    implementation(libs.javaSemver)
    implementation(libs.commons.configuration)
    implementation(libs.commons.configuration2)
    implementation(libs.commons.io)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jgit.core)
    implementation(libs.github.api)
    // Include annotations used by the above library to avoid compiler warnings.
    compileOnly(libs.findbugsAnnotations)
    compileOnly(libs.bridge.method.annotation) {
        exclude(group = "org.jenkins-ci")
    }
    // Gradle Plugins
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.spotless.plugin.gradle)
    implementation(libs.gradle.download.task)
    implementation(libs.launch4j)
    implementation(libs.install4j.gradle)
    implementation(libs.japicmp.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
}
