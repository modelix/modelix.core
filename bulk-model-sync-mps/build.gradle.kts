import org.modelix.mpsHomeDir

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":mps-model-adapters"))

    compileOnly(
        fileTree(mpsHomeDir).matching {
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
