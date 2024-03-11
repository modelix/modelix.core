plugins {
    id("org.modelix.model-api-gen")
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
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
val kotlinGenDir = project.layout.buildDirectory.dir("metamodel/kotlin").get().asFile.apply { mkdirs() }

dependencies {
    mps("com.jetbrains:mps:2021.2.5")
    api("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
}

val mpsDir = project.layout.buildDirectory.dir("mps").get().asFile.apply { mkdirs() }

val resolveMps by tasks.registering(Copy::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val repoDir = projectDir.resolve("test-repo")

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(kotlinGenDir)
    }
    jvmToolchain(11)
}

metamodel {
    dependsOn(resolveMps)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    modulesFrom(projectDir.parentFile.resolve("test-repo"))
    includeLanguage("GraphLang")
    registrationHelperName = "org.modelix.model.sync.bulk.gradle.test.GraphLanguagesHelper"
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
