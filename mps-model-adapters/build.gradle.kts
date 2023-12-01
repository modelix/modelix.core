plugins {
    kotlin("jvm")
    `maven-publish`
}

val mpsZip by configurations.creating
val mpsVersion = project.findProperty("mps.version").toString()

dependencies {
    api(project(":model-api"))

    mpsZip("com.jetbrains:mps:$mpsVersion")
    val mpsZipTree = zipTree({ mpsZip.singleFile }).matching {
        include("lib/**/*.jar")
    }
    compileOnly(mpsZipTree)

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
