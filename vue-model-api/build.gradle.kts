import com.github.gradle.node.npm.task.NpmTask
import dev.petuska.npm.publish.task.NpmPackTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    base
    alias(libs.plugins.node)
    alias(libs.plugins.npm.publish)
    id("modelix-project-repositories")
}

val updateModelClient = tasks.create<NpmTask>("updateModelClient") {
    val modelClientPackage = "../model-client/build/npmDevPackage/model-client.tgz"
    inputs.file(modelClientPackage)
    outputs.cacheIf { true }
    outputs.file("package-lock.json")
    outputs.file("package.json")
    args.set(listOf("install", modelClientPackage))
    dependsOn(":model-client:packJsPackage")
}

tasks.npmInstall {
    dependsOn(":ts-model-api:build")
    dependsOn(updateModelClient)
}

tasks.named("npm_run_build") {
    inputs.dir("src")
    inputs.file("tsconfig.json")

    outputs.cacheIf { true }

    outputs.dir("dist")
}

tasks.named("npm_run_test") {
    inputs.dir("src")
    inputs.file("tsconfig.json")
    inputs.file("jest.config.js")
    outputs.cacheIf { true }
}

tasks.named("npm_run_lint") {
    inputs.dir("src")
    inputs.file("tsconfig.json")
    inputs.file(".eslintrc.js")
    outputs.cacheIf { true }
}

val packageJsonForProd = layout.buildDirectory.file("package-for-publishing.json").get().asFile
val createPackageJsonForPublishing = tasks.create("createPackageJsonForPublishing") {
    dependsOn(updateModelClient)

    val packageJsonForDev = projectDir.resolve("package.json")
    inputs.file(packageJsonForDev)
    inputs.property("project.version", project.version)
    outputs.cacheIf { true }
    outputs.file(packageJsonForProd)

    doLast {
        // We cannot use the mechanisms from the [lugin npm-publish `dev.petuska.npm.publish`,
        // because cannot remove fields from the template package.json and does not override dependency versions.
        @Suppress("UNCHECKED_CAST")
        val packageJsonData = JsonSlurper().parse(packageJsonForDev) as MutableMap<String, Any>

        packageJsonData.set("version", project.version)

        // The relative path to the index.js changes,
        // because the package.json is copied into dist
        packageJsonData.set("main", "index.js")
        // `files` also do not need to be specified,
        // because the package.json is copied into dist
        packageJsonData.remove("files")

        // remove overrides which where needed for development and builds
        packageJsonData.remove("overrides")

        // replaces versions which where needed for development and builds,
        // with its published versions
        @Suppress("UNCHECKED_CAST")
        val dependencies = packageJsonData["dependencies"] as MutableMap<String, String>
        dependencies["@modelix/model-client"] = "^${project.version}"

        // remove key value pairs that where used for comments
        val packageJsonDataIterator = packageJsonData.iterator()
        while (packageJsonDataIterator.hasNext()) {
            val key = packageJsonDataIterator.next().key
            if (key.endsWith("-comment")) {
                packageJsonDataIterator.remove()
            }
        }
        packageJsonForProd.parentFile.mkdirs()
        packageJsonForProd.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(packageJsonData)))
    }
}

tasks.assemble {
    dependsOn("npm_run_build")
}

tasks.check {
    dependsOn("npm_run_prettier")
    dependsOn("npm_run_lint")
    dependsOn("npm_run_test")
}

tasks.clean {
    delete("dist", "tsconfig.tsbuildinfo")
}

npmPublish {
    registries {
        register("itemis-npm-open") {
            uri.set("https://artifacts.itemis.cloud/repository/npm-open")
            System.getenv("NODE_AUTH_TOKEN").takeIf { !it.isNullOrBlank() }?.let {
                authToken.set(it)
            }
        }
    }
    packages {
        create("js") {
            packageJsonTemplateFile.set(packageJsonForProd)
            files {
                setFrom("dist")
            }
        }
    }
}

tasks.named("assembleJsPackage") {
    dependsOn("npm_run_build")
    dependsOn(createPackageJsonForPublishing)
}

tasks.named<NpmPackTask>("packJsPackage") {
    dependsOn("assembleJsPackage")
    packageDir.set(layout.buildDirectory.dir("packages/js"))
    outputFile.set(layout.buildDirectory.file("npmDevPackage/${project.name}.tgz"))
}

tasks.assemble {
    dependsOn("packJsPackage")
}
