import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
    id("org.modelix.model-api-gen") apply false
}

val updateDependencies = tasks.create<NpmTask>("updateDependencies") {
    args.set(
        listOf(
            "install",
            "../../model-client/build/npmDevPackage/model-client.tgz",
            "../../ts-model-api",
        ),
    )
}

tasks.npmInstall {
    dependsOn(updateDependencies)
}

tasks.named("npm_run_build") {
    dependsOn(":metamodel-export:generateMetaModelSources")
    dependsOn("npm_run_test")
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

tasks.named("npm_run_test") {
    dependsOn(":metamodel-export:generateMetaModelSources")
}

tasks.register<NpmTask>("packJsPackage") {
    dependsOn("npm_run_build")
    args.set(listOf("pack", "--pack-destination", "build"))
}
