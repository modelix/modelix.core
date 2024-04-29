import org.modelix.mpsHomeDir

plugins {
    base
    kotlin("jvm")
}

group = "org.modelix.mps"

dependencies {
    implementation(project(":model-api-gen-runtime"))
    implementation(project(":model-api-gen"))
    compileOnly(fileTree(mpsHomeDir.map { it.dir("lib") }).matching { include("**/*.jar") })
    compileOnly(
        mpsHomeDir.map {
            it.files(
                "languages/languageDesign/jetbrains.mps.lang.structure.jar",
                "languages/languageDesign/jetbrains.mps.lang.core.jar",
            )
        },
    )
    implementation(project(":model-api"))
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
