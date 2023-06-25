plugins {
    kotlin("jvm")
    `maven-publish`
    alias(libs.plugins.ktlint)
}

dependencies {
    api(project(":model-api"))

    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2021.1.4")

    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.logging)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
