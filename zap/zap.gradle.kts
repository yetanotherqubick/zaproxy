import japicmp.model.JApiChangeStatus
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.zaproxy.gradle.spotless.ValidateImports
import org.zaproxy.zap.japicmp.AcceptMethodAbstractNowDefaultRule
import org.zaproxy.zap.tasks.GradleBuildWithGitRepos
import org.zaproxy.zap.tasks.UpdateLegalNotice
import org.zaproxy.zap.tasks.internal.JapicmpExcludedData
import java.time.LocalDate

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.japicmp)
    alias(libs.plugins.cyclonedx)
    id("org.zaproxy.java-conventions")
    alias(libs.plugins.crowdin)
    org.zaproxy.zap.distributions
    org.zaproxy.zap.installers
    org.zaproxy.zap.`github-releases`
    org.zaproxy.zap.jflex
    org.zaproxy.zap.publish
    org.zaproxy.zap.spotless
    org.zaproxy.zap.test
}

group = "org.zaproxy"
val versionBC = project.property("zap.japicmp.baseversion") as String

val versionLangFile = "1"
val creationDate by extra { project.findProperty("creationDate") ?: LocalDate.now().toString() }
val distDir = file("src/main/dist/")

fun isZapRelease() = System.getenv("ZAP_RELEASE") != null

val zapJavaVersion by extra { if (isZapRelease()) JavaVersion.toVersion(System.getenv("ZAP_JAVA_VERSION")) else JavaVersion.VERSION_17 }

java {
    // Compile with appropriate Java version when building ZAP releases.
    if (isZapRelease()) {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(zapJavaVersion.majorVersion))
        }
    } else {
        sourceCompatibility = zapJavaVersion
        targetCompatibility = zapJavaVersion
    }
}

crowdin {
    credentials {
        token.set(System.getenv("CROWDIN_AUTH_TOKEN"))
    }

    configuration {
        file.set(file("gradle/crowdin.yml"))
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
    }
}

spotless {
    java {
        addStep(
            ValidateImports.create(
                mapOf(
                    "import org.apache.commons.lang." to
                        "Import/use classes from Commons Lang 3, instead of Lang 2.",
                    "import org.apache.commons.codec." to
                        "Do not use import org.apache.commons.codec.",
                ),
            ),
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(zapJavaVersion.majorVersion.toInt())
    options.compilerArgs = options.compilerArgs + "-parameters"
    val javaVersion = if (isZapRelease()) zapJavaVersion else JavaVersion.current()
    if (javaVersion >= JavaVersion.VERSION_21) {
        options.compilerArgs = options.compilerArgs + "-Xlint:-this-escape"
    }
}

dependencies {
    // Public API dependencies.
    api(libs.commons.configuration)
    api(libs.commons.httpclient) {
        exclude(group = "commons-codec", module = "commons-codec")
        exclude(group = "commons-logging", module = "commons-logging")
    }
    api(libs.harlib)
    api(libs.hsqldb)
    api(libs.javahelp)
    api(libs.jerichoHtml)
    api(variantOf(libs.jsonlib) { classifier("jdk15") })
    api(platform(libs.log4j.bom))
    api(libs.log4j.api)
    api(libs.log4j.api12)
    api(libs.rsyntaxtextarea)
    api(libs.swingx)

    // Implementation dependencies.
    implementation(libs.commons.collections4)
    implementation(libs.commons.csv)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.logging)
    implementation(libs.commons.text)
    implementation(libs.flatlaf)
    implementation(libs.jackson.core.asl)
    implementation(libs.javaSemver)
    implementation(libs.jfreechart)
    implementation(libs.jgraphtcore)
    implementation(libs.log4j.core)

    // Runtime dependencies.
    runtimeOnly(libs.commons.beanutils)
    runtimeOnly(libs.commons.codec)
    // Retained for add-ons that still use the Commons Collections 3.x package.
    runtimeOnly(libs.commons.collections3)
    runtimeOnly(libs.flatlaf.swingx)
    // Loaded by java.util.logging by class name; no static reference exists in ZAP's code.
    runtimeOnly(libs.log4j.jul)
    runtimeOnly(libs.xom) {
        setTransitive(false)
    }

    // Annotation types needed only at compile time.
    compileOnly(libs.bndAnnotation)
    compileOnly(libs.findbugsAnnotations)
    testCompileOnly(libs.bndAnnotation)
    testCompileOnly(libs.findbugsAnnotations)

    // Shared test dependencies.
    testImplementation(libs.assertj.core)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.hamcrest)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito)

    // Test runtime dependencies.
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platformLauncher)
    testRuntimeOnly(files(distDir))

    // GUI-specific test dependencies; common test dependencies are inherited.
    testGuiImplementation(libs.assertj.swing)
}

tasks.register<JavaExec>("run") {
    group = ApplicationPlugin.APPLICATION_GROUP
    description = "Runs ZAP from source, using the default dev home."

    mainClass.set("org.zaproxy.zap.ZAP")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = distDir
}

val runtimeClasspath = configurations.named("runtimeClasspath")

listOf("jar", "jarDaily", "jarWithBom").forEach {
    tasks.named<Jar>(it) {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirPermissions {
            unix("0755")
        }
        filePermissions {
            unix("0644")
        }

        val attrs =
            mapOf(
                "Main-Class" to "org.zaproxy.zap.ZAP",
                "Implementation-Version" to archiveVersion,
                "Create-Date" to creationDate,
                "Class-Path" to
                    runtimeClasspath.flatMap { it.elements }.map { files ->
                        files.map { "lib/${it.asFile.name}" }.sorted().joinToString(" ")
                    },
            )

        manifest {
            attributes(attrs)
        }

        if (System.getenv("ZAP_CHALK") != null) {
            doLast {
                providers.exec {
                    workingDir(rootDir)
                    executable("chalk")
                    args("insert", archiveFile.get().asFile)
                }
            }
        }
    }
}

val japicmp by tasks.registering(JapicmpTask::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks ${project.name}.jar binary compatibility with latest version ($versionBC)."

    oldClasspath.from(zapJar(versionBC))
    newClasspath.from(tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME))
    ignoreMissingClasses.set(true)

    val excludedDataFile = layout.projectDirectory.file("gradle/japicmp.yaml")
    inputs.file(excludedDataFile)

    val excludedData =
        providers.fileContents(excludedDataFile).asText.map(JapicmpExcludedData::fromYaml)
    packageExcludes.set(excludedData.map { it.packageExcludes })
    fieldExcludes.set(excludedData.map { it.fieldExcludes })
    classExcludes.set(excludedData.map { it.classExcludes })
    methodExcludes.set(excludedData.map { it.methodExcludes })

    richReport {
        destinationDir.set(layout.buildDirectory.dir("reports/japicmp"))
        reportName.set("japi.html")
        addDefaultRules.set(true)
        addRule(JApiChangeStatus.MODIFIED, AcceptMethodAbstractNowDefaultRule::class.java)
    }
}

tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
    dependsOn(japicmp)
}

tasks.named<Javadoc>("javadoc") {
    title = "Zed Attack Proxy"
    source =
        sourceSets["main"].allJava.matching {
            include("ch/**")
            include("org/parosproxy/**")
            include("org/zaproxy/**")
        }
    (options as StandardJavadocDocletOptions).run {
        links("https://docs.oracle.com/javase/8/docs/api/")
        encoding = "UTF-8"
        source("${java.targetCompatibility}")
    }
}

val langPack by tasks.registering(Zip::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Assembles the language pack for the Core Language Files add-on."

    archiveFileName.set(
        layout.buildDirectory
            .file("langpack/ZAP_${project.version}_language_pack.$versionLangFile.zaplang")
            .get()
            .asFile.absolutePath,
    )
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    into("lang") {
        from(File(distDir, "lang"))
        from("src/main/resources/org/zaproxy/zap/resources") {
            include("Messages.properties")
        }
    }
}

tasks.register<Copy>("copyLangPack") {
    group = "ZAP Misc"
    description = (
        "Copies the language pack into the Core Language Files add-on " +
            "(assumes zap-extensions repo is in same directory as zaproxy)."
    )

    from(langPack)
    into("$rootDir/../zap-extensions/addOns/coreLang/src/main/zapHomeFiles/lang/")
}

val copyWeeklyAddOns by tasks.registering(GradleBuildWithGitRepos::class) {
    group = "ZAP Misc"
    description = "Copies the weekly add-ons into plugin dir, built from local repos."

    repositoriesDirectory.set(rootDir.parentFile)
    repositoriesDataFile.set(file("src/main/weekly-add-ons.json"))
    cloneRepositories.set(false)
    updateRepositories.set(false)

    val outputDir = file("src/main/dist/plugin/")
    tasks {
        register("copyZapAddOn") {
            args.set(listOf("--into=$outputDir"))
        }
    }
}

val generateAllApiEndpoints by tasks.registering {
    group = "ZAP Misc"
    description = "Generates (and copies) the ZAP API endpoints for all languages."
}

listOf(
    "org.zaproxy.zap.extension.api.DotNetAPIGenerator",
    "org.zaproxy.zap.extension.api.GoAPIGenerator",
    "org.zaproxy.zap.extension.api.JavaAPIGenerator",
    "org.zaproxy.zap.extension.api.NodeJSAPIGenerator",
    "org.zaproxy.zap.extension.api.PhpAPIGenerator",
    "org.zaproxy.zap.extension.api.PythonAPIGenerator",
    "org.zaproxy.zap.extension.api.RustAPIGenerator",
    "org.zaproxy.zap.extension.api.WikiAPIGenerator",
).forEach {
    val langName = it.removePrefix("org.zaproxy.zap.extension.api.").removeSuffix("APIGenerator")
    val task =
        tasks.register<JavaExec>("generate${langName}ApiEndpoints") {
            group = "ZAP Misc"
            description = "Generates (and copies) the ZAP API endpoints for $langName."

            mainClass.set(it)
            classpath = sourceSets["main"].runtimeClasspath
            workingDir = file("$rootDir")
        }

    generateAllApiEndpoints {
        dependsOn(task)
    }
}

launch4j {
    setJarTask(tasks.named<Jar>("jar").get())
}

fun zapJar(version: String): File {
    val oldGroup = group
    try {
        // https://discuss.gradle.org/t/is-the-default-configuration-leaking-into-independent-configurations/2088/6
        group = "virtual_group_for_japicmp"
        val dependency = dependencies.create("$oldGroup:$name:$version")
        return configurations
            .detachedConfiguration(dependency)
            .apply {
                isTransitive = false
            }.singleFile
    } finally {
        group = oldGroup
    }
}

val runtimeLibraryArtifacts =
    runtimeClasspath.flatMap { configuration ->
        configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts
                .map { artifact ->
                    val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
                    "${id.group}:${id.module}\t${artifact.file.name}"
                }.sorted()
        }
    }

tasks.register<UpdateLegalNotice>("updateLegalNotice") {
    group = "ZAP Misc"
    description = "Updates the third-party library table in LEGALNOTICE.md."

    classpath.from(runtimeClasspath)
    libraryArtifacts.set(runtimeLibraryArtifacts)
    licensesFile.set(file("gradle/thirdPartyLicenses.yaml"))
    legalNoticeFile.set(rootProject.file("LEGALNOTICE.md"))
}
