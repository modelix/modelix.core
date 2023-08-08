import com.github.gradle.node.npm.task.NpmSetupTask

buildscript {
    repositories {
        mavenLocal()
        maven { url = uri("https://repo.maven.apache.org/maven2") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    }

    dependencies {
    }
}

repositories {
    mavenLocal()
    maven { url = uri("https://repo.maven.apache.org/maven2") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("base")
    id("org.modelix.model-api-gen")
    id("com.github.node-gradle.node") version "3.4.0"
}

val mps by configurations.creating

fun scriptFile(relativePath: String): File {
    return file("$rootDir/build/$relativePath")
}

val mpsDir = buildDir.resolve("mps")

val modelixCoreVersion: String = projectDir.resolve("../version.txt").readText()

val kotlinGenDir = buildDir.resolve("metamodel/kotlin_gen")

dependencies {
    mps("com.jetbrains:mps:2021.1.4")
    implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
    implementation("org.modelix:modelql-typed:$modelixCoreVersion")
    implementation("org.modelix:modelql-untyped:$modelixCoreVersion")

    testImplementation(kotlin("test-common"))
    testImplementation(kotlin("test-annotations-common"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))

    testImplementation("org.modelix:model-api:$modelixCoreVersion")
    testImplementation("org.modelix:model-client:$modelixCoreVersion")
    testImplementation("org.modelix:model-server-lib:$modelixCoreVersion")
    testImplementation("org.modelix:modelql-client:$modelixCoreVersion")

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cors)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.html.builder)
    testImplementation(libs.ktor.server.auth)
    testImplementation(libs.ktor.server.auth.jwt)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.server.forwarded.header)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.logback.classic)

    testImplementation(
        files(kotlinGenDir) {
            builtBy("generateMetaModelSources")
        },
    )
}

tasks.test {
    useJUnitPlatform()
}

val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

sourceSets["main"].kotlin {
    srcDir(kotlinGenDir)
}

metamodel {
    mpsHeapSize = "2g"
    dependsOn(resolveMps)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    kotlinProject = project
    typescriptDir = projectDir.resolve("typescript_src")
    includeNamespace("jetbrains")
    exportModules("jetbrains.mps.baseLanguage")

    names {
        languageClass.prefix = "L_"
        languageClass.baseNameConversion = { it.replace(".", "_") }
        typedNode.prefix = ""
        typedNodeImpl.suffix = "Impl"
    }
    registrationHelperName = "org.modelix.apigen.test.ApigenTestLanguages"
}

node {
    version.set("18.3.0")
    npmVersion.set("8.11.0")
    download.set(true)
}

tasks.withType<NpmSetupTask> {
    dependsOn("generateMetaModelSources")
}

tasks.named("npm_run_build") {
    inputs.dir("typescript_src")
    inputs.file("package.json")
    inputs.file("package-lock.json")

    outputs.dir("dist")
}

tasks.named("assemble") {
    dependsOn("npm_run_build")
}
