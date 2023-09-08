plugins {
    id("org.modelix.model-api-gen")
    `maven-publish`
    kotlin("jvm") version "1.9.0"
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    mavenCentral()
}

val modelixCoreVersion = file("../../version.txt").readText()

version = modelixCoreVersion

val mps: Configuration by configurations.creating
val kotlinGenDir = buildDir.resolve("metamodel/kotlin").apply { mkdirs() }

dependencies {
    mps("com.jetbrains:mps:2021.2.5")
    api("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
}

val mpsDir = buildDir.resolve("mps").apply { mkdirs() }

val resolveMps by tasks.registering(Copy::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val repoDir = projectDir.resolve("test-repo")

val copyMetamodelToMpsHome by tasks.registering(Copy::class) {
    from(file(projectDir.resolve("../test-repo/languages")))
    into(file(mpsDir.resolve("languages").apply { mkdirs() }))
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(kotlinGenDir)
    }
}

metamodel {
    dependsOn(resolveMps)
    dependsOn(copyMetamodelToMpsHome)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    includeLanguage("GraphLang")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.modelix"
            from(components["kotlin"])
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.named("processResources") {
    dependsOn("generateMetaModelSources")
}

tasks.named("compileKotlin") {
    dependsOn("generateMetaModelSources")
}
