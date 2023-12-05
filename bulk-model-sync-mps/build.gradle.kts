plugins {
    kotlin("jvm")
}

val mpsVersion = project.findProperty("mps.version").toString()
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

dependencies {
    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":mps-model-adapters"))

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["kotlin"])
        }
    }
}

tasks.register("mpsCompatibility") { dependsOn("build") }
