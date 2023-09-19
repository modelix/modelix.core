plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
}

val mpsZip by configurations.creating
val ideaZip by configurations.creating
val mpsExtensionsZip by configurations.creating

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    // modelix deps from the same project
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    implementation(project(":model-api", configuration = "jvmRuntimeElements"))
    implementation(project(":model-datastructure", configuration = "jvmRuntimeElements"))
    implementation(project(":mps-model-adapters"))

    // implementation("io.github.jetbrains.mps-extensions:de.slisson.mps.reflection.runtime:2020.3.2-SNAPSHOT")

    // MPS dependencies
    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-workbench:2021.1.4")
    compileOnly("com.jetbrains:mps-platform:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2021.1.4")
    compileOnly("com.jetbrains.intellij.idea:ideaIC:211.7628.21")

    // extracting jars from zipped products
    mpsZip("com.jetbrains:mps:2021.1.4")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
            include("lib/mps-persistence.jar")
        },
    )

    ideaZip("com.jetbrains.intellij.idea:ideaIC:211.7628.21")
    compileOnly(
        zipTree({ ideaZip.singleFile }).matching {
            include("util.jar")
        },
    )

    // TODO clarify it with Sascha, if we have to copy the ReflectionUtil to us simply, so we'll not have any dependency for MPS Extensions
    mpsExtensionsZip("de.itemis.mps:extensions:2021.1.+")
    implementation(
        zipTree({ mpsExtensionsZip.singleFile }).matching {
            include("de.itemis.mps.extensions/de.slisson.mps.hacks/languages/de.slisson.mps.hacks/de.slisson.mps.reflection.runtime.jar")
        },
    )
}

group = "org.modelix.mps"
description = "Generic helper library to sync model-server content with MPS"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
