plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val kotlinxSerializationVersion: String by rootProject
val kotlinCollectionsImmutableVersion: String by rootProject

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinCollectionsImmutableVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("com.charleskorn.kaml:kaml:0.40.0")
    implementation(project(":metamodel-runtime"))
    implementation("com.squareup:kotlinpoet:1.12.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

description = "Generator for Kotlin meta model classes"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}