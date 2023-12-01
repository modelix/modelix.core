plugins {
    kotlin("jvm")
    `maven-publish`
}

val mpsVersion = project.findProperty("mps.version").toString()
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

dependencies {
    api(project(":model-api"))

    compileOnly(
        mpsHome.map {
            it.asFileTree.matching {
                include("lib/**/*.jar")
            }
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

tasks.register("mpsCompatibility") { dependsOn("build") }
