import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.openapi.generator)
}

description = "OpenAPI specifications for modelix components"

defaultTasks.add("build")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":model-api"))
    implementation(project(":model-server-api"))

    implementation(libs.google.gson)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.resources)
}

// OpenAPI integration
val basePackage = project.group.toString()
val openAPIgenerationPath = "${project.layout.buildDirectory.get()}/generated/openapi"

// Pairs of the different OpenAPI files we use. Each pair must have its own 'category' as first argument as these
// are used to generate corresponding packages
val openApiFiles = listOf(
    "public" to "model-server",
    "operative" to "model-server-operative",
    "light" to "model-server-light",
    "html" to "model-server-html",
    "deprecated" to "model-server-deprecated",
)

// generate tasks for each OpenAPI file
openApiFiles.forEach {
    val targetTaskName = "openApiGenerate-${it.second}"
    val targetPackageName = "$basePackage.api.${it.first}"
    val outputPath = "$openAPIgenerationPath/${it.first}"
    tasks.register<GenerateTask>(targetTaskName) {
        // we let the Gradle OpenAPI generator plugin build data classes and API interfaces based on the provided
        // OpenAPI specification. That way, the code is forced to stay in sync with the API specification.
        generatorName.set("kotlin-server")
        inputSpec.set(layout.projectDirectory.dir("openapi/specifications").file("${it.second}.yaml").toString())
        outputDir.set(outputPath)
        packageName.set(targetPackageName)
        apiPackage.set(targetPackageName)
        modelPackage.set(targetPackageName)
        // We use patched mustache so that only the necessary parts (i.e. resources and models)
        // are generated. additionally we patch the used serialization framework as the `ktor` plugin
        // uses a different one than we do in the model-server. The templates are based on
        // https://github.com/OpenAPITools/openapi-generator/tree/809b3331a95b3c3b7bcf025d16ae09dc0682cd69/modules/openapi-generator/src/main/resources/kotlin-server
        templateDir.set("$projectDir/openapi/templates")
        configOptions.set(
            mapOf(
                // we use the ktor generator to generate server side resources and model (i.e. data classes)
                "library" to "ktor",
                // the generated artifacts are not built independently, thus no dedicated build files have to be generated
                "omitGradleWrapper" to "true",
                // the path to resource generation we need
                "featureResources" to "true",
                // disable features we do not use
                "featureAutoHead" to "false",
                "featureCompression" to "false",
                "featureHSTS" to "false",
                "featureMetrics" to "false",
            ),
        )
        // generate only Paths and Models - only this set will produce the intended Paths.kt as well as the models
        // the openapi generator is generally very picky and configuring it is rather complex
        globalProperties.putAll(
            mapOf(
                "models" to "",
                "apis" to "",
                "supportingFiles" to "Paths.kt",
            ),
        )
    }

    // Ensure that the OpenAPI generator runs before starting to compile
    tasks.named("processResources") {
        dependsOn(targetTaskName)
    }
    tasks.named("compileKotlin") {
        dependsOn(targetTaskName)
    }
    tasks.named("runKtlintCheckOverMainSourceSet") {
        dependsOn(targetTaskName)
    }

    // do not apply ktlint on the generated files
    ktlint {
        filter {
            exclude {
                it.file.toPath().toAbsolutePath().startsWith(outputPath)
            }
        }
    }

    // add openAPI generated artifacts to the sourceSets
    sourceSets["main"].kotlin.srcDir("$outputPath/src/main/kotlin")
}
