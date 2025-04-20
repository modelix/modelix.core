
plugins {
    kotlin("jvm")
    alias(libs.plugins.jib)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":mps-git-import"))
    implementation(libs.modelix.buildtools.lib)
}
