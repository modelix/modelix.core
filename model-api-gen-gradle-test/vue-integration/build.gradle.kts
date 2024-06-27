import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

val updateDependencies = tasks.register<NpmTask>("updateDependencies") {
    dependsOn(":typescript-generation:packJsPackage")
    args.set(
        listOf(
            "install",
            "../../model-client/build/npmDevPackage/model-client.tgz",
            "../../vue-model-api/build/npmDevPackage/vue-model-api.tgz",
            "../typescript-generation/build/typescript-generation-0.0.0.tgz",
        ),
    )
}

tasks.npmInstall {
    dependsOn(updateDependencies)
}

tasks.check {
    dependsOn("npm_run_test")
}
