plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    // modelix deps from the same project
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    implementation(project(":model-api", configuration = "jvmRuntimeElements"))
    implementation(project(":model-datastructure", configuration = "jvmRuntimeElements"))

    // MPS dependencies
    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-workbench:2021.1.4")
    compileOnly("com.jetbrains:mps-platform:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2021.1.4")
    compileOnly("com.jetbrains.intellij.idea:ideaIC:211.7628.21")
}

description = "Generic helper library to sync model-server content with MPS"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
