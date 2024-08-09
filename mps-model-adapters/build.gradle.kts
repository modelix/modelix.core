import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm`
    `maven-publish`
}

dependencies {
    api(project(":model-api"))
    implementation(libs.modelix.incremental)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/*.jar")
        },
    )

    implementation(libs.trove4j)
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.logging)
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
