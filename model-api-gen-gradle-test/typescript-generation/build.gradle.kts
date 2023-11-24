import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
    id("org.modelix.model-api-gen") apply false
}

tasks.named("npm_run_build") {
    dependsOn(":metamodel-export:generateMetaModelSources")
    inputs.dir(layout.buildDirectory.dir("typescript_src"))
    inputs.file("package.json")
    inputs.file("package-lock.json")

    outputs.dir("dist")
}

tasks.assemble {
    dependsOn("npm_run_build")
}

tasks.clean {
    dependsOn("npm_run_clean")
}

tasks.register<NpmTask>("packJsPackage") {
    dependsOn("npm_run_build")
    args.set(listOf("pack", "--pack-destination", "build"))
}
