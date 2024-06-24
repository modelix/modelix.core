import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

val updateDependencies = tasks.create<NpmTask>("updateDependencies") {
    args.set(
        listOf(
            "install",
            "../../model-client/build/npmDevPackage/model-client.tgz",
        ),
    )
}

tasks.npmInstall {
    dependsOn(updateDependencies)
}

tasks.check {
    dependsOn("npm_run_test")
}
