plugins {
    kotlin("jvm")
    `maven-publish`
    alias(libs.plugins.ktlint)
}

dependencies {
    api(project(":model-api"))

    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2022.3")

    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.logging)
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "model-adapters"
            from(components["kotlin"])
        }
    }
}
