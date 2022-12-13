# Gradle Plugin for the Meta-Model Generator

This plugin exports MPS language structure models as JSON and then generates them Kotlin/TypeScript classes to provide
a type-safe model API for the Modelix model-api.

# Usage

## gradle.properties

```
modelixCoreVersion=1.4.10
mpsVersion=2021.3.2
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
val modelixCoreVersion: String by rootProject

val mpsDir = buildDir.resolve("mps")

val mps by configurations.creating
val mpsDependencies by configurations.creating
dependencies {
    mps("com.jetbrains:mps:$mpsVersion")
}

val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

metamodel {
    dependsOn(resolveMps)
    mpsHome = mpsDir
    
    modulesFrom(projectDir.resolve("languages"))
    modulesFrom(projectDir.resolve("solutions"))
    includeNamespace("org.example")
    includeLanguage("language.fq.name")
    includeConcept("concept.fq.name")
    
    kotlinProject = project(":my-kotlin-project")
    kotlinDir = project(":my-kotlin-project").projectDir.resolve("src/main/kotlin_gen")
    registrationHelperName = "org.example.MyLanguages"
    
    typescriptDir = project(":my-typescript-project").projectDir.resolve("src/gen")
}
```