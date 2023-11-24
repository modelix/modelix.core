plugins {
    kotlin("jvm")
    `maven-publish`
}

val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2021.1.4"

dependencies {
    api(project(":model-api"))
    implementation(libs.modelix.incremental)

    val mpsZip by configurations.creating
    mpsZip("com.jetbrains:mps:$mpsVersion")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
            include("lib/*.jar")
        },
    )

    implementation(libs.trove)
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
