import org.modelix.mpsHomeDir
import org.modelix.mpsMajorVersion

plugins {
    `modelix-mps-plugin`
}

dependencies {
    val excludeMPSLibraries: (ModuleDependency).() -> Unit = {
        exclude("org.slf4j", "slf4j-api")
    }

    implementation(project(":mps-git-import"), excludeMPSLibraries)
    implementation(project(":bulk-model-sync-lib"), excludeMPSLibraries)
    implementation(project(":bulk-model-sync-mps"), excludeMPSLibraries)
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    implementation(project(":model-client"), excludeMPSLibraries)
    implementation(project(":datastructures"), excludeMPSLibraries)
    implementation(libs.modelix.mpsApi, excludeMPSLibraries)
    implementation(libs.kotlin.logging, excludeMPSLibraries)
    implementation(libs.kotlin.datetime, excludeMPSLibraries)
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )

    // testImplementation("junit:junit:4.13.2")
    testImplementation(libs.testcontainers)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

tasks {
    test {
        dependsOn(":model-server:jibDockerBuild")
        jvmArgs("-Dmodelix.model.server.image=modelix/model-server:$version")
        onlyIf {
            mpsMajorVersion == "2024.1"
        }
        jvmArgs("-Xmx1000m")
    }
}
