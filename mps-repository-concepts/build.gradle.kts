import org.gradle.internal.jvm.Jvm
import org.modelix.mpsHomeDir

plugins {
    base
    `maven-publish`
    alias(libs.plugins.modelix.mps.buildtools)
}

group = "org.modelix.mps.modules"

mpsBuild {
    mpsHome = mpsHomeDir.get().asFile.absolutePath
    javaHome = Jvm.current().javaHome
    disableParentPublication()

    search("modules")
    publication("repositoryconcepts") {
        module("org.modelix.model.repositoryconcepts")
    }
}
