package org.zaproxy.zap

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify
import org.apache.tools.ant.filters.ReplaceTokens
import org.apache.tools.ant.taskdefs.condition.Os
import org.cyclonedx.gradle.CyclonedxAggregateTask
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.zaproxy.zap.tasks.internal.MainAddOnsData
import org.zaproxy.zap.tasks.CreateDmg
import org.zaproxy.zap.tasks.DownloadMainAddOns
import org.zaproxy.zap.tasks.GradleBuildWithGitRepos
import org.zaproxy.zap.tasks.UpdateMainAddOns

plugins {
    de.undercouch.download
}

val dailyVersion = provider { "D-${extra["creationDate"]}" }

val bundledResourcesPath = "src/main/resources/org/zaproxy/zap/resources"

// Keep in sync with zapJavaVersion in zap/zap.gradle.kts.
val distributionJavaVersion =
    if (providers.environmentVariable("ZAP_RELEASE").isPresent) {
        JavaVersion.toVersion(providers.environmentVariable("ZAP_JAVA_VERSION").get()).majorVersion
    } else {
        JavaVersion.VERSION_17.majorVersion
    }

val cyclonedxRuntimeBom by tasks.registering(CyclonedxDirectTask::class) {
    aggregateConfigurationName.set("cyclonedxBom")
    includeConfigs.set(listOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))
    jsonOutput.set(layout.buildDirectory.file("reports/bom-runtime/bom.json"))
    xmlOutput.unsetConvention()
}

val jar by tasks.existing(Jar::class)
val jarWithBom by tasks.registering(Jar::class) {
    destinationDirectory.set(layout.buildDirectory.dir("libs/withBom/"))

    from(jar.map { it.source }) {
        exclude("MANIFEST.MF")
    }
    from(cyclonedxRuntimeBom)
}

tasks.named<CyclonedxAggregateTask>("cyclonedxBom") {
    jsonOutput.set(layout.buildDirectory.file("reports/bom-all/bom.json"))
    xmlOutput.set(layout.buildDirectory.file("reports/bom-all/bom.xml"))
}

val versionCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val macOsJreVersion = versionCatalog.findVersion("macos-jre").get().requiredVersion

val mainAddOnsFile = file("src/main/main-add-ons.yml")

val downloadMainAddOns by tasks.registering(DownloadMainAddOns::class) {
    group = "build"
    description = "Downloads the add-ons included in main (non-SNAPSHOT) releases."

    addOnsData.set(mainAddOnsFile)
    outputDir.set(layout.buildDirectory.dir("mainAddOns"))
}

val updateMainAddOns by tasks.registering(UpdateMainAddOns::class) {
    group = "build"
    description = "Updates the main add-ons from a ZapVersions.xml file."

    addOnsData.set(mainAddOnsFile)
    addOnsDataUpdated.set(mainAddOnsFile)
}

val bundledAddOns: Any = provider {
    if (version.toString().endsWith("SNAPSHOT")) {
        file("src/main/dist/plugin")
    } else {
        downloadMainAddOns
    }
}

val distFiles by tasks.registering(Sync::class) {
    val distributionDir = layout.projectDirectory.dir("src/main/dist").asFile
    val startScriptTokens =
        mapOf(
            "zapJar" to jarWithBom.get().archiveFileName.get(),
            "javaVersion" to distributionJavaVersion,
        )

    into(layout.buildDirectory.dir("distFiles"))
    from(jarWithBom)
    from(distributionDir) {
        filesMatching(listOf("zap.bat", "zap.sh")) {
            filter<ReplaceTokens>("tokens" to startScriptTokens)
        }
        exclude("README.weekly")
        exclude("plugin/*.zap")
    }
    from("src/main/resources/resource/zap.ico")
    from(configurations.named("runtimeClasspath")) {
        into("lib")
    }
    from("$bundledResourcesPath/xml") {
        into("xml")
    }
    from(bundledResourcesPath) {
        include("config.xml", "log4j.properties")
        into("xml")
    }
    from(bundledResourcesPath) {
        include("Messages.properties", "vulnerabilities.xml")
        into("lang")
    }
    from(bundledResourcesPath) {
        include("zapdb.script")
        into("db")
    }
    from(bundledResourcesPath) {
        include("ApacheLicense-2.0.txt")
        into("license")
    }
}

tasks.register<Zip>("distCrossplatform") {
    group = "Distribution"
    description = "Bundles the crossplatform distribution."

    archiveFileName.set("ZAP_${project.version}_Crossplatform.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val topLevelDir = "ZAP_${project.version}"
    from(distFiles) {
        into(topLevelDir)
    }
    from(bundledAddOns) {
        into("$topLevelDir/plugin")
        exclude("Readme.txt")
    }
}

val coreAddOnIds =
    providers.fileContents(layout.projectDirectory.file("src/main/main-add-ons.yml")).asText.map { content ->
        ObjectMapper(YAMLFactory())
            .readValue(content, MainAddOnsData::class.java)
            .getAddOns()
            .filter { it.isCore() }
            .mapTo(mutableSetOf()) { it.getId() }
    }

val copyCoreAddOns by tasks.registering(Sync::class) {
    inputs.file(mainAddOnsFile)
    if (!version.toString().endsWith("SNAPSHOT")) {
        dependsOn(bundledAddOns)
    }

    from(bundledAddOns) {
        include(
            *coreAddOnIds.get().map { "**/$it-*.zap" }.toTypedArray(),
        )
    }
    into(layout.buildDirectory.dir("coreAddOns"))
}

tasks.register<Zip>("distCore") {
    group = "Distribution"
    description = "Bundles the core distribution."

    archiveFileName.set("ZAP_${project.version}_Core.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val topLevelDir = "ZAP_${project.version}"

    from(distFiles) {
        into(topLevelDir)
    }
    from(copyCoreAddOns) {
        into("$topLevelDir/plugin")
        exclude("Readme.txt")
    }
}

tasks.register<Tar>("distLinux") {
    group = "Distribution"
    description = "Bundles the Linux distribution."

    archiveFileName.set("ZAP_${project.version}_Linux.tar.gz")
    compression = Compression.GZIP
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val topLevelDir = "ZAP_${project.version}"
    from(distFiles) {
        into(topLevelDir)
    }
    from(bundledAddOns) {
        into("$topLevelDir/plugin")
        exclude(listOf("Readme.txt", "*macos*.zap", "*windows*.zap"))
    }
}

listOf(
    MacArch("", "", "", "x64", "x86_64", "false", "0fe26252c258ec239ea6d39a6a1f42b75025bff0d237e9ab3acb4782cef29439"),
    MacArch("Arm64", "_aarch64", " (ARM64)", "aarch64", "arm64", "true", "b37759cce74d3104da243c5a4ca1f8a73d6d8811b4a1711028744ec5559f7eb0")
).forEach { it ->

    val volumeName = "ZAP"
    val appName = "$volumeName.app"
    val macOsJreDir = layout.buildDirectory.dir("macOsJre${it.suffix}").get().asFile
    val macOsJreUnpackDir = File(macOsJreDir, "unpacked")
    val macOsJreFile = File(macOsJreDir, "jdk$macOsJreVersion-jre.tar.gz")

    val downloadMacOsJre = tasks.register<Download>("downloadMacOsJre${it.suffix}") {
        src("https://api.adoptium.net/v3/binary/version/jdk-$macOsJreVersion/mac/${it.arch}/jre/hotspot/normal/eclipse?project=jdk")
        dest(macOsJreFile)
        connectTimeout(60_000)
        readTimeout(60_000)
        onlyIfModified(true)
        doFirst {
            require(Os.isFamily(Os.FAMILY_MAC)) {
                "To build the macOS distribution the OS must be macOS."
            }
        }
    }

    val verifyMacOsJre = tasks.register<Verify>("verifyMacOsJre${it.suffix}") {
        dependsOn(downloadMacOsJre)
        src(macOsJreFile)
        algorithm("SHA-256")
        checksum(it.checksum)
    }

    val unpackMacOSJre = tasks.register<Sync>("unpackMacOSJre${it.suffix}") {
        dependsOn(verifyMacOsJre)
        from(tarTree(macOsJreFile)) {
            eachFile {
                val separator = path.indexOf('/')
                if (separator >= 0) {
                    path = "jre-${path.substring(0, separator)}${path.substring(separator)}"
                }
            }
        }
        into(macOsJreUnpackDir)
    }

    val lsArchitecture = it.lsArchitecture
    val lsRequiresNativeExecution = it.lsRequiresNativeExecution
    val zapVersion = version.toString()
    val jarWithBomFileName = jarWithBom.flatMap { it.archiveFileName }

    val macOsDistDataDir = layout.buildDirectory.dir("macOsDistData${it.suffix}").get().asFile
    val prepareDistMac = tasks.register<Sync>("prepareDistMac${it.suffix}") {
        into(macOsDistDataDir)
        from(unpackMacOSJre) {
            into("$appName/Contents/PlugIns/")
        }
        from("src/main/macOS/") {
            filesMatching("**/Info.plist") {
                filter<ReplaceTokens>(
                    "tokens" to mapOf(
                        "JREDIR" to macOsJreUnpackDir.listFiles()[0].name,
                        "SHORT_VERSION_STRING" to zapVersion,
                        "VERSION_STRING" to "2",
                        "ZAPJAR" to jarWithBomFileName.get(),
                        "LS_ARCHITECTURE" to lsArchitecture,
                        "LS_REQUIRES_NATIVE_EXECUTION" to lsRequiresNativeExecution
                    )
                )
            }
        }
        from("src/main/resources/resource/ZAP.icns") {
            into("$appName/Contents/Resources/")
        }
        val zapDir = "$appName/Contents/Java/"
        from(distFiles) {
            into(zapDir)
            exclude(listOf("zap.bat", "zap.ico"))
        }
        from(bundledAddOns) {
            into("$zapDir/plugin")
            exclude(listOf("Readme.txt", "*linux*.zap", "*windows*.zap"))
        }

    }

    tasks.register<CreateDmg>("distMac${it.suffix}") {
        group = "Distribution"
        description = "Bundles the macOS${it.taskDesc} distribution."

        dependsOn(prepareDistMac)

        volname.set(volumeName)
        workingDir.set(macOsDistDataDir)
        dmg.set(layout.buildDirectory.file("distributions/${volumeName}_$version${it.fileNameSuffix}.dmg"))

        doFirst {
            val symlink = Paths.get("$macOsDistDataDir/Applications")
            if (Files.notExists(symlink)) {
                Files.createSymbolicLink(symlink, Paths.get("/Applications"))
            }
        }
    }
}

val jarDaily by tasks.registering(Jar::class) {
    archiveVersion.set(dailyVersion)

    from(jarWithBom.map { it.source }) {
        exclude("MANIFEST.MF")
    }
}

val distDaily by tasks.registering(Zip::class) {
    group = "Distribution"
    description = "Bundles the daily distribution."

    archiveFileName.set(dailyVersion.map { "ZAP_$it.zip" })
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val rootDir = "ZAP_${dailyVersion.get()}"
    val startScripts = listOf("zap.bat", "zap.sh")
    val distributionDir = layout.projectDirectory.dir("src/main/dist").asFile
    val jarDailyFileName = jarDaily.get().archiveFileName.get()
    val jarWithBomFileName = jarWithBom.get().archiveFileName.get()
    val startScriptTokens =
        mapOf(
            "zapJar" to jarDailyFileName,
            "javaVersion" to distributionJavaVersion,
        )

    from(jarDaily) {
        into(rootDir)
    }
    from(distributionDir) {
        into(rootDir)
        include(startScripts)
        filesMatching(startScripts) {
            filter<ReplaceTokens>("tokens" to startScriptTokens)
        }
    }
    from(File(distributionDir, "plugin")) {
        into("$rootDir/plugin")
        include("*.zap")
    }
    from(distributionDir) {
        into(rootDir)
        include("README.weekly")
        rename { "README" }
    }
    from(distFiles) {
        into(rootDir)
        exclude(jarWithBomFileName)
        exclude("README")
        exclude(startScripts)
    }
}

tasks.named("assemble") {
    dependsOn(distDaily)
}

val weeklyAddOnsDir = layout.buildDirectory.dir("weeklyAddOns")
val buildWeeklyAddOns by tasks.registering(GradleBuildWithGitRepos::class) {
    group = "Distribution"
    description = "Builds the weekly add-ons from source for weekly distribution."

    repositoriesDirectory.set(temporaryDir)
    outputDirectory.set(weeklyAddOnsDir)
    repositoriesDataFile.set(file("src/main/weekly-add-ons.json"))
    clean.set(true)
    quiet.set(System.getenv("ZAP_WEEKLY_QUIET") != "false")

    tasks {
        if (System.getenv("ZAP_WEEKLY_ADDONS_NO_TEST") != "true") {
            create("test")
        }
        create("copyZapAddOn").args.set(listOf("--into=${weeklyAddOnsDir.get().asFile}"))
    }
}

val prepareDistWeekly by tasks.registering(Sync::class) {

    dependsOn(buildWeeklyAddOns)

    val startScripts = listOf("zap.bat", "zap.sh")
    val distributionDir = layout.projectDirectory.dir("src/main/dist").asFile
    val jarDailyFileName = jarDaily.get().archiveFileName.get()
    val jarWithBomFileName = jarWithBom.get().archiveFileName.get()
    val startScriptTokens =
        mapOf(
            "zapJar" to jarDailyFileName,
            "javaVersion" to distributionJavaVersion,
        )

    from(jarDaily)
    from(distributionDir) {
        include(startScripts)
        filesMatching(startScripts) {
            filter<ReplaceTokens>("tokens" to startScriptTokens)
        }
    }
    from(weeklyAddOnsDir) {
        into("plugin")
    }
    from(distributionDir) {
        include("README.weekly")
        rename { "README" }
    }
    from(distFiles) {
        exclude(jarWithBomFileName)
        exclude("README")
        exclude(startScripts)
    }
    into(layout.buildDirectory.dir("distFilesWeekly"))
}

tasks.register<Zip>("distWeekly") {
    group = "Distribution"
    description = "Bundles the weekly distribution."

    archiveFileName.set(dailyVersion.map { "ZAP_WEEKLY_$it.zip" })
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(prepareDistWeekly) {
        into("ZAP_${dailyVersion.get()}")
    }
}

data class MacArch(
    val suffix: String,
    val fileNameSuffix: String,
    val taskDesc: String,
    val arch: String,
    val lsArchitecture: String,
    val lsRequiresNativeExecution: String,
    val checksum: String
)
