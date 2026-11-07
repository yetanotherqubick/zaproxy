import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.sonarqube)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    checkConstraints = true
}

apply(from = "$rootDir/gradle/ci.gradle.kts")

sonarqube {
    properties {
        property("sonar.projectKey", "zaproxy_zaproxy")
        property("sonar.organization", "zaproxy")
        property("sonar.host.url", "https://sonarcloud.io")
        // Workaround https://sonarsource.atlassian.net/browse/SONARGRADL-126
        property("sonar.exclusions", "**/*.gradle.kts")
    }
}
