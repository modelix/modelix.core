
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
    kotlin("jvm") version "1.8.10"
    id("base")
    id("org.modelix.model-api-gen")
}

val mps by configurations.creating

fun scriptFile(relativePath: String): File {
    return file("$rootDir/build/$relativePath")
}

val mpsDir = buildDir.resolve("mps")

val modelixCoreVersion: String = projectDir.resolve("../version.txt").readText()

dependencies {
    mps("com.jetbrains:mps:2021.1.4")
    implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
}

val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val kotlinGenDir = buildDir.resolve("metamodel/kotlin_gen")
sourceSets["main"].kotlin {
    srcDir(kotlinGenDir)
}

metamodel {
    mpsHeapSize = "2g"
    dependsOn(resolveMps)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    kotlinProject = project
    includeNamespace("jetbrains")
    //exportModules("jetbrains.mps.baseLanguage")

    names {
        languageClass.prefix = "L_"
        languageClass.baseNameConversion = { it.replace(".", "_") }
        typedNode.prefix = ""
        typedNodeImpl.suffix = "Impl"
    }
}