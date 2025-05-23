import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm-with-junit`
    `maven-publish`
}

dependencies {
    api(project(":model-api"))
    api(project(":mps-multiplatform-lib"))
    implementation(project(":model-datastructure"))
    implementation(libs.modelix.incremental)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/*.jar")
        },
    )

    implementation(libs.trove4j)
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.logging)
    implementation(libs.modelix.mpsApi)
}

group = "org.modelix.mps"

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "model-adapters"
            from(components["java"])
        }
    }
}
