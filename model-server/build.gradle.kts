import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer

plugins {
    application
    id("com.diffplug.spotless")
    `maven-publish`
    id("com.adarshr.test-logger") version "3.2.0"
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization")
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
    implementation(libs.ktor.serialization.json)

    implementation(libs.bundles.ignite)

    implementation(libs.postgresql)

    implementation(libs.apache.commons.io)
    implementation(libs.guava)
    implementation(libs.jcommander)

    testImplementation(libs.bundles.apache.cxf)
    testImplementation(libs.junit)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
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

val fatJarFile = file("$buildDir/libs/model-server-latest-fatJar.jar")
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
            // Change glue for your project package where the step definitions are.
            // And where the feature files are.
            args = listOf("--plugin", "pretty", "--glue", "org.modelix.model.server.functionaltests", "src/test/resources/functionaltests")

        }
    }
}

tasks.named("test") {
    dependsOn("cucumber")
}

task("copyLibs", Sync::class) {
    into("$buildDir/dependency-libs")
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
        googleJavaFormat("1.8").aosp()
        licenseHeader("/*\n" +
                """ * Licensed under the Apache License, Version 2.0 (the "License");""" + "\n" +
                """ * you may not use this file except in compliance with the License.""" + "\n" +
                """ * You may obtain a copy of the License at""" + "\n" +
                """ *""" + "\n" +
                """ *  http://www.apache.org/licenses/LICENSE-2.0"""+ "\n" +
                """ *""" + "\n" +
                """ * Unless required by applicable law or agreed to in writing,""" + "\n" +
                """ * software distributed under the License is distributed on an""" + "\n" +
                """ * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY""" + "\n" +
                """ * KIND, either express or implied.  See the License for the""" + "\n" +
                """ * specific language governing permissions and limitations""" + "\n" +
                """ * under the License. """ + "\n" +
                " */\n"+
                "\n")
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
                ' * under the License. \n' +
                ' */\n' +
                '\n'*/
    }
}

