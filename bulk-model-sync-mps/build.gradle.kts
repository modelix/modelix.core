plugins {
    kotlin("jvm")
}

val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2021.1.4"

val mpsZip by configurations.creating

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":mps-model-adapters"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))

    mpsZip("com.jetbrains:mps:$mpsVersion")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
            include("lib/**/*.jar")
        },
    )

    implementation(libs.trove)

    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.serialization.json)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["kotlin"])
        }
    }
}
