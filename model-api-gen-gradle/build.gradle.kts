
plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    java

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    kotlin("jvm")
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation(project(":model-api-gen"))
    implementation(project(":model-api"))
}

gradlePlugin {
    // Define the plugin
    val mpsMetaModel by plugins.creating {
        id = "org.modelix.model-api-gen"
        implementationClass = "org.modelix.metamodel.gradle.MetaModelGradlePlugin"
    }
}

val writeVersionFile by tasks.registering {
    val propertiesFile = projectDir.resolve("src/main/resources/modelix.core.version.properties")
    propertiesFile.parentFile.mkdirs()
    propertiesFile.writeText(
        """
        modelix.core.version=$version
        """.trimIndent(),
    )
}
tasks.named("processResources") {
    dependsOn(writeVersionFile)
}
