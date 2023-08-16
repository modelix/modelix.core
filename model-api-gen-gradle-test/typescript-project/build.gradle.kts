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
    id("base")
    alias(libs.plugins.node)
}


val modelixCoreVersion: String = projectDir.resolve("../../version.txt").readText()

node {
    version.set("18.3.0")
    npmVersion.set("8.11.0")
    download.set(true)
}

tasks.named("npm_run_build") {
    dependsOn(":kotlin-project:assembleJsPackage")
    inputs.dir("src/gen")
    inputs.dir(project(":kotlin-project").buildDir.resolve("packages/js"))
    inputs.file("package.json")
    inputs.file("package-lock.json")

    outputs.dir("dist")
}

tasks.named("assemble") {
    dependsOn("npm_run_build")
}

tasks.withType<NpmSetupTask> {
    dependsOn(":apigen-project:generateMetaModelSources")
    dependsOn(":kotlin-project:assembleJsPackage")
}
