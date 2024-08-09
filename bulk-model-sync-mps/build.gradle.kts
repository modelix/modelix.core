import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm`
}

dependencies {
    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":mps-model-adapters"))
    implementation(project(":model-datastructure"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )

    implementation(libs.trove4j)

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
