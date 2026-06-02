import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import io.gitlab.arturbosch.detekt.Detekt
import org.modelix.registerVersionGenerationTask

plugins {
    application
    `maven-publish`
    alias(libs.plugins.test.logger)
    alias(libs.plugins.shadow)
    `modelix-kotlin-jvm-with-junit`
    kotlin("plugin.serialization")
    alias(libs.plugins.jib)
}

description = "Model Server offering access to model storage"

defaultTasks.add("build")

// Apache Ignite accesses proprietary JDK APIs that the module system keeps inaccessible by default.
// These flags open up those APIs and must be passed to every JVM that starts Ignite
// (tests, `run`, install scripts, container).
// List taken verbatim from the Ignite documentation:
// https://ignite.apache.org/docs/latest/quick-start/java#running-ignite-with-java-11-or-later
val igniteJvmArgs = listOf(
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
    "--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED",
    "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED",
    "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.math=ALL-UNNAMED",
    "--add-opens=java.sql/java.sql=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.time=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.logging/java.util.logging=ALL-UNNAMED",
    "--add-opens=java.management/sun.management=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
)

val mpsExtensionsVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)

    implementation(project(":streams"))
    implementation(project(":model-api"))
    api(project(":model-server-api"))
    api(project(":model-server-openapi"))
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
    implementation(libs.ktor.server.compression)
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
        mergeStrategy = PropertiesFileTransformer.MergeStrategy.Append
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
    // Propagates to the `run` task and the generated start scripts (installDist / distributions).
    applicationDefaultJvmArgs = igniteJvmArgs
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

tasks.withType<Detekt> {
    exclude("**/org/modelix/api/**")
}

project.registerVersionGenerationTask("org.modelix.model.server")

tasks.test {
    // Workaround Ignite failing locally because the autogenerated node ID results in an invalid path name.
    // See https://stackoverflow.com/questions/76387714/apache-ignite-failing-on-startup
    environment("IGNITE_OVERRIDE_CONSISTENT_ID", "node00")
    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
    jvmArgs(igniteJvmArgs)
}

jib {
    from.image = "registry.access.redhat.com/ubi8/openjdk-17:1.20-2.1729094551"
    to.image = "modelix/model-server:$version"
    to.tags = setOf("test")
    container {
        ports = listOf("28101")
        jvmFlags = igniteJvmArgs
    }
}
