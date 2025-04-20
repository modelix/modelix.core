import org.modelix.mpsHomeDir

plugins {
    `modelix-kotlin-jvm-with-junit`
    alias(libs.plugins.jib)
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

jib {
    from {
        image = "modelix/mps-git-import-base"
    }
    to {
        image = "modelix/mps-git-import"
    }
}
