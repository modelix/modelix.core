
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

jib {
    from.image = "modelix/mps-base-image:2024.3@sha256:fdaebe708aabe17add687143a585b06a7eb412b3529eff413bc37b2c13dfac8e"
}
