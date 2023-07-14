import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:versioning-plugin:1.8.10")
    }
}

plugins {
    `maven-publish`
    `version-catalog`
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gitVersion)
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.tasktree)
    id("org.jetbrains.dokka") version "1.8.20"
}

repositories {
    mavenLocal()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    mavenCentral()
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
        gitVersion().let { if (it.endsWith("-SNAPSHOT")) it else "$it-SNAPSHOT" }.also { versionFile.writeText(it) }
    }
}

dependencies {
    dokkaPlugin("org.jetbrains.dokka:versioning-plugin:1.8.10")
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    version = rootProject.version
    group = rootProject.group

    tasks.withType<DokkaTaskPartial>().configureEach {
        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            footerMessage = createFooterMessage()
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
        }
    }

    repositories {
        mavenLocal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        mavenCentral()
    }

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
                if (project.name in arrayOf("model-client",
                                "model-client-js",
                                "model-client-jvm",
                                "model-server",
                                "model-server-api")){
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
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    dependsOn(":ts-model-api:npm_run_build")
}
val docsDir = buildDir.resolve("dokka")

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
        a("https://api.modelix.org")  {+"api.modelix.org"}
        +", modelix, and the modelix framework, are licensed under the "
        a("https://www.apache.org/licenses/LICENSE-2.0.html") { +"Apache-2.0 license"}
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
                    id ="leftColumn"
                }
                div {
                    id = "main"
                    div("main-content") {
                        id ="content"
                        div("breadcrumbs")
                        div("cover") {
                            h2 { +"Available versions:" }
                            div("table") {
                                val versionDirs = docsDir.listFiles()
                                    ?.filter { it.isDirectory }
                                    ?.sortedByDescending { it.name }
                                if (versionDirs != null) {
                                    for (versionDir in versionDirs) {
                                        val versionIndex = versionDir.resolve("index.html")
                                        if (versionIndex.exists()) {
                                            div("table-row") {
                                                div("main-subrow") {
                                                    div("w-100") {
                                                        span("inline-flex") {
                                                            a( href = versionIndex.relativeTo(docsDir).path) {
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
        }
    }
}
