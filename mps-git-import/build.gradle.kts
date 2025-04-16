import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm-with-junit`
    `maven-publish`
}

dependencies {
    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":bulk-model-sync-mps"))
    implementation(project(":mps-model-adapters"))
    implementation(project(":model-client"))
    implementation(project(":datastructures"))
    implementation(libs.modelix.mpsApi)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.datetime)
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")
    api("com.github.ajalt.clikt:clikt:5.0.3")

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )
}

group = "org.modelix.mps"

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "git-import"
            from(components["java"])
        }
    }
}
