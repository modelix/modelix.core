# Gradle Plugin for the Meta-Model Generator

This plugin exports MPS language structure models as JSON and then generates them Kotlin/TypeScript classes to provide
a type-safe model API for the Modelix model-api.

# Usage

## gradle.properties

```
modelixCoreVersion=1.4.7
mpsVersion=2021.3.2
mpsExtensionsVersion=2021.3.2496.3b163cd
```

## settings.gradle.kts

```
pluginManagement {
    val modelixCoreVersion: String by settings
    plugins {
        id("org.modelix.metamodel.gradle") version modelixCoreVersion
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
}
```

## build.gradle.kts

```
plugins {
    id("org.modelix.metamodel.gradle")
}

val mpsVersion: String by rootProject
val mpsExtensionsVersion: String by rootProject
val modelixCoreVersion: String by rootProject

val mpsDependenciesDir = buildDir.resolve("mpsDependencies")
val mpsDir = buildDir.resolve("mps")

val mps by configurations.creating
val mpsDependencies by configurations.creating
dependencies {
    mps("com.jetbrains:mps:$mpsVersion")
    mpsDependencies("de.itemis.mps:extensions:$mpsExtensionsVersion")
}

val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val resolveMpsDependencies by tasks.registering(Sync::class) {
    from(mpsDependencies.resolve().map { zipTree(it) })
    into(mpsDependenciesDir)
}

metamodel {
    dependsOn(resolveMps, resolveMpsDependencies)
    mpsHome = mpsDir
    
    modulesFrom(mpsDependenciesDir)
    modulesFrom(projectDir.resolve("languages"))
    modulesFrom(projectDir.resolve("solutions"))
    includeNamespace("org.ki.embedded")
    includeLanguage("org.modelix.model.repositoryconcepts")
    
    kotlinProject = project(":my-kotlin-project")
    kotlinDir = project(":my-kotlin-project").projectDir.resolve("src/main/kotlin_gen")
    registrationHelperName = "org.example.MyLanguages"
    
    typescriptDir = project(":my-typescript-project").projectDir.resolve("src/gen")
}
```