import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import io.gitlab.arturbosch.detekt.Detekt
import org.modelix.registerVersionGenerationTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    application
    `maven-publish`
    alias(libs.plugins.test.logger)
    alias(libs.plugins.shadow)
    `modelix-kotlin-jvm-with-junit`
    kotlin("plugin.serialization")
    alias(libs.plugins.openapi.generator)
}

description = "Model Server offering access to model storage"

defaultTasks.add("build")

val mpsExtensionsVersion: String by project

val openApiSpec by configurations.creating

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)

    implementation(project(":streams"))
    implementation(project(":model-api"))
    implementation(project(":model-server-api"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    api(project(":model-datastructure", configuration = "jvmRuntimeElements"))
    implementation(project(":modelql-server"))
    implementation(project(":authorization"))
    implementation(project(":bulk-model-sync-lib"))
    implementation(libs.apache.commons.lang)

    implementation(libs.apache.commons.collections)
    implementation(libs.logback.classic)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)

    implementation(libs.bundles.ignite)

    implementation(libs.postgresql)

    implementation(libs.apache.commons.io)
    implementation(libs.guava)
    implementation(libs.jcommander)

    openApiSpec(
        project(
            mapOf(
                "path" to ":model-server-openapi",
                "configuration" to "openApiSpec",
            ),
        ),
    )

    testImplementation(libs.bundles.apache.cxf)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.coreJvm)
    testImplementation(libs.kotest.assertions.ktor)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.jsoup)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":modelql-untyped"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.keycloak.authz.client)
    testImplementation(libs.keycloak.admin.client)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("model-server")
    archiveClassifier.set("fatJar")
    archiveVersion.set("latest")
    manifest {
        attributes["Main-Class"] = "org.modelix.model.server.Main"
    }

    mergeServiceFiles()
    append("META-INF/spring.schemas")
    append("META-INF/spring.handlers")
    append("META-INF/spring.tooling")

    transform(PropertiesFileTransformer::class.java) {
        paths = listOf("META-INF/spring.factories")
        mergeStrategy = "append"
    }
}

val fatJarFile = project.layout.buildDirectory.file("libs/model-server-latest-fatJar.jar")
val fatJarArtifact = artifacts.add("archives", fatJarFile) {
    type = "jar"
    builtBy("shadowJar")
}

tasks.named("assemble") {
    dependsOn("installDist")
}

application {
    mainClass.set("org.modelix.model.server.Main")
}

publishing {
    publications {
        create<MavenPublication>("modelServer") {
            groupId = project.group as String
            artifactId = "model-server"
            version = project.version as String

            from(components["java"])
        }

        create<MavenPublication>("modelServerWithDependencies") {
            groupId = project.group as String
            artifactId = "model-server-with-dependencies"
            artifact(fatJarArtifact)
        }
    }
}

// copies the openAPI specifications from the api folder into a resource
// folder so that they are packaged and deployed with the model-server
val specSourceDir = project.layout.buildDirectory.dir("openapi/src/main/resources")
val copyApi = tasks.register<Copy>("copyApi") {
    dependsOn(openApiSpec)

    from(openApiSpec.resolve().first())
    into(specSourceDir.get().dir("api"))
}
sourceSets["main"].resources.srcDir(specSourceDir)

tasks.named("processResources") {
    dependsOn(copyApi)
}

// OpenAPI integration
val openApiGenerationPath = project.layout.buildDirectory.get().dir("generated/openapi")
val restApiPackage = "org.modelix.model.server.handlers"
val openApiGenerate = tasks.register<GenerateTask>("openApiGenerateModelServer") {
    dependsOn(openApiSpec)

    // we let the Gradle OpenAPI generator plugin build data classes and API interfaces based on the provided
    // OpenAPI specification. That way, the code is forced to stay in sync with the API specification.
    generatorName.set("kotlin-server")
    inputSpec.set(openApiSpec.resolve().first().toString())
    outputDir.set(openApiGenerationPath.toString())
    packageName.set(restApiPackage)
    apiPackage.set(restApiPackage)
    modelPackage.set(restApiPackage)
    // We use patched mustache so that only the necessary parts (i.e. resources and models)
    // are generated. additionally we patch the used serialization framework as the `ktor` plugin
    // uses a different one than we do in the model-server. The templates are based on
    // https://github.com/OpenAPITools/openapi-generator/tree/809b3331a95b3c3b7bcf025d16ae09dc0682cd69/modules/openapi-generator/src/main/resources/kotlin-server
    templateDir.set("${layout.projectDirectory.dir("src/main/resources/openapi/templates")}")
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
tasks.named("compileKotlin") {
    dependsOn(openApiGenerate)
}

// add openAPI generated artifacts to the sourceSets
sourceSets["main"].kotlin.srcDir("$openApiGenerationPath/src/main/kotlin")

tasks.withType<Detekt> {
    exclude("**/org/modelix/api/**")
}

project.registerVersionGenerationTask("org.modelix.model.server")

tasks.test {
    // Workaround Ignite failing locally because the autogenerated node ID results in an invalid path name.
    // See https://stackoverflow.com/questions/76387714/apache-ignite-failing-on-startup
    environment("IGNITE_OVERRIDE_CONSISTENT_ID", "node00")
    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}
