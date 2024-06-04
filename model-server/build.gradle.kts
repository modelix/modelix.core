import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import io.gitlab.arturbosch.detekt.Detekt
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    application
    `maven-publish`
    alias(libs.plugins.spotless)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.shadow)
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.openapi.generator)
}

description = "Model Server offering access to model storage"

defaultTasks.add("build")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

val mpsExtensionsVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)

    implementation(project(":model-api"))
    implementation(project(":model-server-api"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    api(project(":model-datastructure", configuration = "jvmRuntimeElements"))
    implementation(project(":modelql-server"))
    implementation(project(":authorization"))
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
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.swagger)

    implementation(libs.bundles.ignite)

    implementation(libs.postgresql)

    implementation(libs.apache.commons.io)
    implementation(libs.guava)
    implementation(libs.jcommander)

    testImplementation(libs.bundles.apache.cxf)
    testImplementation(libs.junit)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.jsoup)
    testImplementation(kotlin("test"))
    testImplementation(project(":modelql-untyped"))

    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
}

tasks.test {
    useJUnitPlatform()
}

val cucumberRuntime by configurations.creating {
    extendsFrom(configurations["testImplementation"])
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

val cucumber = task("cucumber") {
    dependsOn("shadowJar", "compileTestJava")
    doLast {
        javaexec {
            mainClass.set("io.cucumber.core.cli.Main")
            classpath = cucumberRuntime + sourceSets.main.get().output + sourceSets.test.get().output
            args = listOf(
                "--plugin",
                "pretty",
                // Enable junit reporting so that GitHub actions can report on these tests, too
                "--plugin",
                "junit:${project.layout.buildDirectory.dir("test-results/cucumber.xml").get()}",
                // Change glue for your project package where the step definitions are.
                "--glue",
                "org.modelix.model.server.functionaltests",
                // Specify where the feature files are.
                "src/test/resources/functionaltests",
            )
        }
    }
}

// copies the openAPI specifications from the api folder into a resource
// folder so that they are packaged and deployed with the model-server
tasks.register<Copy>("copyApis") {
    from(project.layout.projectDirectory.dir("../model-server-openapi/specifications"))
    include("*.yaml")
    into(project.layout.buildDirectory.dir("openapi/src/main/resources/api"))
    sourceSets["main"].resources.srcDir(project.layout.buildDirectory.dir("openapi/src/main/resources/"))
}

tasks.named("compileKotlin") {
    dependsOn("copyApis")
}

tasks.named("build") {
    dependsOn("cucumber")
    dependsOn("copyApis")
}

tasks.named("processResources") {
    dependsOn("copyApis")
}

task("copyLibs", Sync::class) {
    into(project.layout.buildDirectory.dir("dependency-libs"))
    from(configurations.runtimeClasspath)
}

tasks.named("assemble") {
    finalizedBy("copyLibs")
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

spotless {
    java {
        googleJavaFormat("1.18.1").aosp()
        licenseHeader(
            "/*\n" +
                """ * Licensed under the Apache License, Version 2.0 (the "License");""" + "\n" +
                """ * you may not use this file except in compliance with the License.""" + "\n" +
                """ * You may obtain a copy of the License at""" + "\n" +
                """ *""" + "\n" +
                """ *  http://www.apache.org/licenses/LICENSE-2.0""" + "\n" +
                """ *""" + "\n" +
                """ * Unless required by applicable law or agreed to in writing,""" + "\n" +
                """ * software distributed under the License is distributed on an""" + "\n" +
                """ * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY""" + "\n" +
                """ * KIND, either express or implied.  See the License for the""" + "\n" +
                """ * specific language governing permissions and limitations""" + "\n" +
                """ * under the License.""" + "\n" +
                " */\n" +
                "\n",
        )
        /*licenseHeader '/*\n' +
                ' * Licensed under the Apache License, Version 2.0 (the "License");\n' +
                ' * you may not use this file except in compliance with the License.\n' +
                ' * You may obtain a copy of the License at\n' +
                ' *\n' +
                ' *  http://www.apache.org/licenses/LICENSE-2.0\n' +
                ' *\n' +
                ' * Unless required by applicable law or agreed to in writing,\n' +
                ' * software distributed under the License is distributed on an\n' +
                ' * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n' +
                ' * KIND, either express or implied.  See the License for the\n' +
                ' * specific language governing permissions and limitations\n' +
                ' * under the License.\n' +
                ' */\n' +
                '\n'*/
    }
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
        inputSpec.set(layout.projectDirectory.dir("../model-server-openapi/specifications").file("${it.second}.yaml").toString())
        outputDir.set(outputPath)
        packageName.set(targetPackageName)
        apiPackage.set(targetPackageName)
        modelPackage.set(targetPackageName)
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
    tasks.named("processResources") {
        dependsOn(targetTaskName)
    }
    tasks.named("compileKotlin") {
        dependsOn(targetTaskName)
    }

    // add openAPI generated artifacts to the sourceSets
    sourceSets["main"].kotlin.srcDir("$outputPath/src/main/kotlin")
}

tasks.withType<Detekt> {
    exclude("**/org/modelix/api/**")
}
