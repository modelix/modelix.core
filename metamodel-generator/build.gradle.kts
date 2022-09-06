plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
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