plugins {
    `modelix-kotlin-jvm-with-junit`
    kotlin("plugin.serialization")
}

val kotlinxSerializationVersion: String by rootProject
val kotlinCollectionsImmutableVersion: String by rootProject

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.serialization.yaml)
    implementation(project(":model-api-gen-runtime"))
    implementation(project(":modelql-core"))
    implementation(project(":modelql-untyped"))
    implementation(project(":modelql-typed"))
    implementation(libs.kotlinpoet)
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

description = "Generator for Kotlin meta model classes"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
