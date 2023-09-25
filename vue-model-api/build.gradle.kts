import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

tasks.named("npm_run_build") {
    dependsOn(":ts-model-api:build")

    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
}

tasks.named("build") {
    dependsOn("npm_run_build")
}

tasks.named("check") {
    dependsOn("npm_run_lint")
    dependsOn("npm_run_test")
}

val cleanTypeScript = tasks.register<NpmTask>("cleanTypeScript") {
    dependsOn(":npmInstall")
    args.addAll("run", "clean")
}

tasks.named("clean") {
    dependsOn("npm_run_clean")
}
