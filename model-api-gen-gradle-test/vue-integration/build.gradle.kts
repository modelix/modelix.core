import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

val updateDependencies = tasks.create<NpmTask>("updateDependencies") {
    dependsOn(":typescript-generation:packJsPackage")
    dependsOn(":kotlin-generation:packJsPackage")
    args.set(
        listOf(
            "install",
            "../kotlin-generation/build/packages/modelix-model-client-1.0.0.tgz",
            "../../vue-model-api/build/npmDevPackage/vue-model-api.tgz",
            "../typescript-generation/build/typescript-generation-0.0.0.tgz",
            "--save-dev",
        ),
    )
}

tasks.npmInstall {
    dependsOn(updateDependencies)
}

tasks.check {
    dependsOn("npm_run_test")
}
