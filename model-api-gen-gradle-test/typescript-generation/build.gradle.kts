import org.modelix.metamodel.gradle.GenerateMetaModelSources

plugins {
    base
    alias(libs.plugins.node)
    id("org.modelix.model-api-gen") apply false
}

val codeGenerationTask = project(":metamodel-export").tasks.named<GenerateMetaModelSources>("generateMetaModelSources")

tasks.named("npm_run_build") {
    inputs.dir(codeGenerationTask.map { it.typescriptOutputDir })
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
