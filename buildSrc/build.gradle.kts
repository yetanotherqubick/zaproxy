buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    }
}

plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless")
    id(libs.plugins.zaproxy.common.get().pluginId)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

spotless {
    kotlinGradle {
        ktlint()
    }
}

tasks.withType<JavaCompile>().configureEach {
    if (JavaVersion.current().getMajorVersion() >= "21") {
       options.compilerArgs = options.compilerArgs + "-Xlint:-this-escape"
    }
}

dependencies {
    implementation(libs.javaparser.core)
    implementation(libs.javaSemver)
    implementation(libs.commons.codec)
    implementation(libs.commons.configuration)
    implementation(libs.commons.collections)
    implementation(libs.commons.io)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jgit.core)
    implementation(libs.jgit.archive)
    implementation(libs.github.api)
    // Include annotations used by the above library to avoid compiler warnings.
    compileOnly(libs.findbugsAnnotations)
    compileOnly(libs.bridge.method.annotation) {
        exclude(group = "org.jenkins-ci")
    }
    // Gradle Plugins
    implementation(libs.spotless.plugin.gradle)
    implementation(libs.gradle.download.task)
    implementation(libs.launch4j)
    implementation(libs.gradle.publish)
    implementation(libs.japicmp.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
}
