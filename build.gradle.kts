import com.github.gradle.node.NodeExtension
import com.github.gradle.node.NodePlugin
import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.semver.Version

buildscript {
    dependencies {
        classpath(libs.dokka.versioning)
        classpath(libs.semver)
    }
}

plugins {
    `maven-publish`
    `version-catalog`
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gitVersion)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.node) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinx.kover)
}

group = "org.modelix"
description = "Projectional Editor"
version = computeVersion()
println("Version: $version")

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        gitVersion()
            // Avoid duplicated "-SNAPSHOT" ending
            .let { if (it.endsWith("-SNAPSHOT")) it else "$it-SNAPSHOT" }
            // Normalize the version so that is always a valid NPM version.
            .let { if (it.matches("""\d+\.\d+.\d+-.*""".toRegex())) it else "0.0.1-$it" }
            .also { versionFile.writeText(it) }
    }
}

dependencies {
    dokkaPlugin(libs.dokka.versioning)

    // Generate a combined coverage report
    project.subprojects.forEach {
        kover(it)
    }
}

val parentProject = project

subprojects {
    val subproject = this
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    version = rootProject.version
    group = rootProject.group

    tasks.withType<DokkaTaskPartial>().configureEach {
        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            footerMessage = createFooterMessage()
        }
    }

    tasks.withType<Detekt> {
        parallel = true
        // For now, we only use the results here as hints
        ignoreFailures = true

        buildUponDefaultConfig = true
        config.setFrom(parentProject.projectDir.resolve(".detekt.yml"))

        reports {
            sarif.required.set(true)
            // This is required for the GitHub upload action to easily find all sarif files in a single directory.
            sarif.outputLocation.set(parentProject.layout.buildDirectory.file("reports/detekt/${project.name}.sarif"))
            html.required.set(true)
        }
    }

    val kotlinApiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_6
    subproject.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        if (!name.lowercase().contains("test")) {
            this.kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs += listOf("-Xjvm-default=all-compatibility", "-Xexpect-actual-classes")
                apiVersion = kotlinApiVersion.version
            }
        }
    }
    subproject.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        if (!name.lowercase().contains("test")) {
            this.kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
                apiVersion = kotlinApiVersion.version
            }
        }
    }

    subproject.plugins.withType<JavaPlugin> {
        subproject.extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    subproject.plugins.withType<KotlinPlatformJvmPlugin> {
        subproject.extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(11)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    subproject.plugins.withType<KotlinMultiplatformPluginWrapper> {
        subproject.extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(11)
            sourceSets.all {
                if (!name.lowercase().contains("test")) {
                    languageSettings {
                        apiVersion = kotlinApiVersion.version
                    }
                }
            }
        }
    }

    subproject.plugins.withType<NodePlugin> {
        subproject.extensions.configure<NodeExtension> {
            version.set(libs.versions.node)
            download.set(true)
        }
    }
}

allprojects {
    publishing {
        repositories {
            maven {
                name = "itemis"
                url = if (version.toString().contains("SNAPSHOT")) {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                } else {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                }
                credentials {
                    username = project.findProperty("artifacts.itemis.cloud.user").toString()
                    password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                }
            }
            maven {
                name = "GitHubPackages"
                // we moved some components from modelix/modelix to modelix/modelix.core but github packages
                // cannot handle this situation. basically we suffer from what is described here:
                //     https://github.com/orgs/community/discussions/23474
                // this is a simple workaround for the affected components.
                // consequently, when obtaining these dependencies, the repo url is the old modelix/modelix one...
                if (project.name in arrayOf(
                        "model-client",
                        "model-client-js",
                        "model-client-jvm",
                        "model-server",
                        "model-server-api",
                    )
                ) {
                    url = uri("https://maven.pkg.github.com/modelix/modelix")
                    credentials {
                        username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_ACTOR")
                        password = project.findProperty("gpr.universalkey") as? String ?: System.getenv("GHP_UNIVERSAL_TOKEN")
                    }
                } else {
                    url = uri("https://maven.pkg.github.com/modelix/modelix.core")
                    credentials {
                        username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_ACTOR")
                        password = project.findProperty("gpr.key") as? String ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }

    // Set maven metadata for all known publishing tasks. The exact tasks and names are only known after evaluatin.
    afterEvaluate {
        tasks.withType<AbstractPublishToMaven>() {
            this.publication?.apply {
                setMetadata()
            }
        }
    }
}

fun MavenPublication.setMetadata() {
    pom {
        url.set("https://github.com/modelix/modelix.core")
        scm {
            connection.set("scm:git:https://github.com/modelix/modelix.core.git")
            url.set("https://github.com/modelix/modelix.core")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    dependsOn(":ts-model-api:npm_run_build")
}
val docsDir = project.layout.buildDirectory.dir("dokka").get().asFile

tasks.dokkaHtmlMultiModule {
    outputDirectory.set(docsDir.resolve("$version"))
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets += file(projectDir.resolve("dokka/logo-dark.svg"))
        customAssets += file(projectDir.resolve("dokka/logo-icon.svg"))
        customStyleSheets += file(projectDir.resolve("dokka/logo-styles.css"))
        footerMessage = createFooterMessage()
    }
    doLast {
        val index = file(docsDir.resolve("index.html"))
        index.writeText(createDocsIndexPage())
    }
}

fun createFooterMessage(): String {
    return createHTML().span {
        createFooter()
    }
}

fun FlowContent.createFooter() {
    p {
        +"For more information visit "
        a("https://modelix.org") { +"modelix.org" }
        +", for further documentation visit "
        a("https://docs.modelix.org") { +"docs.modelix.org" }
        +"."
    }
    p {
        +"Copyright \u00A9 2021-present by the "
        a("https://modelix.org") { +"modelix open source project" }
        +" and the individual contributors. All Rights reserved."
    }
    p {
        +"Except where otherwise noted, "
        a("https://api.modelix.org") { +"api.modelix.org" }
        +", modelix, and the modelix framework, are licensed under the "
        a("https://www.apache.org/licenses/LICENSE-2.0.html") { +"Apache-2.0 license" }
        +"."
    }
}

fun createDocsIndexPage(): String {
    return createHTML().html {
        head {
            meta(charset = "utf-8")
            link(href = "./$version/styles/style.css", rel = "Stylesheet")
            link(href = "./$version/styles/logo-styles.css", rel = "Stylesheet")
            link(href = "./$version/images/logo-icon.svg", rel = "icon")
            title("modelix.core API Reference")
            style {
                unsafe {
                    +"""
                    .library-name {
                        padding-top: 6px;
                        padding-bottom: 6px;
                    }
                    """.trimIndent()
                }
            }
        }
        body {
            div("navigation-wrapper") {
                id = "navigation-wrapper"
                div("library-name") {
                    a { +"modelix.core API Reference" }
                }
            }
            div("wrapper") {
                id = "container"
                div {
                    id = "leftColumn"
                }
                div {
                    id = "main"
                    div("main-content") {
                        id = "content"
                        div("breadcrumbs")
                        div("cover") {
                            h2 { +"Available versions:" }
                            div("table") {
                                val versionDirs = docsDir.listFiles()
                                    ?.filter {
                                        it.isDirectory && !it.name.startsWith('.') && it.name != "latest"
                                    }
                                    ?.sortedByDescending { Version.parse(it.name) }
                                if (versionDirs != null) {
                                    div("table-row") {
                                        div("main-subrow") {
                                            div("w-100") {
                                                span("inline-flex") {
                                                    a(href = "/latest") {
                                                        +"modelix.core LATEST"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    for (versionDir in versionDirs) {
                                        val versionIndex = versionDir.resolve("index.html")
                                        if (versionIndex.exists()) {
                                            div("table-row") {
                                                div("main-subrow") {
                                                    div("w-100") {
                                                        span("inline-flex") {
                                                            a(href = versionIndex.relativeTo(docsDir).path) {
                                                                +"modelix.core ${versionDir.name}"
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    div("footer") {
                        span("go-to-top-icon") {
                            a("#content") {
                                id = "go-to-top-link"
                            }
                        }
                        span {
                            createFooter()
                        }
                    }
                }
            }
        }
    }
}

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

publishing {
    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.modelix"
            artifactId = "core-version-catalog"
            from(components["versionCatalog"])

            setMetadata()
        }
    }
}
