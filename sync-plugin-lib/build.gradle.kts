plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
}

val mpsToIdeaMap = mapOf(
    "2020.3.6" to "203.8084.24", // https://github.com/JetBrains/MPS/blob/2020.3.6/build/version.properties
    "2021.1.4" to "211.7628.21", // https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties
    "2021.2.6" to "212.5284.40", // https://github.com/JetBrains/MPS/blob/2021.2.5/build/version.properties (?)
    "2021.3.3" to "213.7172.25", // https://github.com/JetBrains/MPS/blob/2021.3.3/build/version.properties
    "2022.2" to "222.4554.10", // https://github.com/JetBrains/MPS/blob/2021.2.1/build/version.properties
    "2022.3" to "223.8836.41", // https://github.com/JetBrains/MPS/blob/2022.3.0/build/version.properties (?)
)
// use the given MPS version, or 2022.2 (last version with JAVA 11) as default
val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2022.2"
if (!mpsToIdeaMap.containsKey(mpsVersion)) {
    throw GradleException("Build for the given MPS version '$mpsVersion' is not supported.")
}
// identify the corresponding intelliJ platform version used by the MPS version
val ideaVersion = mpsToIdeaMap.getValue(mpsVersion)
println("Building for MPS version $mpsVersion and IntlliJ version $ideaVersion")

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
    compileOnly("com.jetbrains:mps-openapi:$mpsVersion")
    compileOnly("com.jetbrains:mps-core:$mpsVersion")
    compileOnly("com.jetbrains:mps-workbench:$mpsVersion")
    compileOnly("com.jetbrains:mps-platform:$mpsVersion")
    compileOnly("com.jetbrains:mps-environment:$mpsVersion")
    compileOnly("com.jetbrains.intellij.idea:ideaIC:$ideaVersion")
    runtimeOnly("com.jetbrains.intellij.platform:util:$ideaVersion")
    implementation("com.intellij:openapi:7.0.3")

    // extracting jars from zipped products
    mpsZip("com.jetbrains:mps:$mpsVersion")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
            include("lib/mps-persistence.jar")
        },
    )

    ideaZip("com.jetbrains.intellij.idea:ideaIC:$ideaVersion")
    compileOnly(
        zipTree({ ideaZip.singleFile }).matching {
            include("util.jar")
        },
    )

    // TODO clarify it with Sascha:
    //  - if we have to copy the ReflectionUtil to us simply, so we'll not have any dependency for MPS Extensions
    //  - if there is a replacement for a subset of shadowmodels already
    mpsExtensionsZip("de.itemis.mps:extensions:2021.1.+")
    implementation(
        zipTree({ mpsExtensionsZip.singleFile }).matching {
            include("de.itemis.mps.extensions/de.slisson.mps.hacks/languages/de.slisson.mps.hacks/de.slisson.mps.reflection.runtime.jar")
            include("de.itemis.mps.extensions/de.q60.shadowmodels/languages/de.q60.shadowmodels/de.q60.mps.incremental.runtime.jar")
            include("de.itemis.mps.extensions/de.q60.shadowmodels/languages/de.q60.shadowmodels/de.q60.mps.shadowmodels.runtime.jar")
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
