plugins {
    id("org.modelix.model-api-gen")
    `modelix-kotlin-jvm-with-junit-platform`
}

repositories {
    gradlePluginPortal()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    mavenCentral()
}

val mps: Configuration by configurations.creating
val kotlinGenDir = project.layout.buildDirectory.dir("metamodel/kotlin").get().asFile.apply { mkdirs() }

dependencies {
    mps("com.jetbrains:mps:2021.2.5")
    api("org.modelix:model-api-gen-runtime")
}

val mpsDir = project.layout.buildDirectory.dir("mps").get().asFile.apply { mkdirs() }

val resolveMps by tasks.registering(Copy::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val repoDir = projectDir.parentFile.resolve("test-repo")

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(kotlinGenDir)
    }
}

metamodel {
    dependsOn(resolveMps)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    modulesFrom(repoDir)
    includeLanguage("GraphLang")
    registrationHelperName = "org.modelix.model.sync.bulk.gradle.test.GraphLanguagesHelper"
}

tasks.named("processResources") {
    dependsOn("generateMetaModelSources")
}

tasks.named("compileKotlin") {
    dependsOn("generateMetaModelSources")
}
