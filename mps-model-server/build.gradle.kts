plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.13.3"
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(project(":model-server-lib"))
    implementation(project(":mps-model-adapters"))
    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2021.1.4")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {

    // IDEA platform version used in MPS 2021.1.4: https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties#L11
    version.set("211.7628.21")

    // type.set("IC") // Target IDE Platform

    // plugins.set(listOf("jetbrains.mps.core", "com.intellij.modules.mps"))
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("232.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
