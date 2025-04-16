import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm-with-junit`
    `maven-publish`
    application
    alias(libs.plugins.modelix.mps.buildtools)
}

dependencies {
    implementation(project(":mps-git-import"))
    implementation(libs.modelix.buildtools.lib)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )
}

application {
    mainClass.set("org.modelix.mps.gitimport.cli.MainKt")
}

tasks.assemble {
    dependsOn(tasks.installDist)
}
