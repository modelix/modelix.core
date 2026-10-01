plugins {
    `kotlin-dsl`
}

dependencies {
    // This should ideally be `compileOnly` and not `implementation`.
    // The compiled `build-logic` should not bundle the plugin code.
    // The project applying code from "build-logic" should add the plugins themselves.
    //
    // But using `compileOnly` we run into https://github.com/gradle/gradle/issues/23709
    // This is indirectly related to applying any settings plugin in our root "settings.gradle.kts".
    // ```
    // plugins {
    //  id("modelix-repositories")
    // }
    // ```
    // Applying any settings plugin causes an `InstrumentingVisitableURLClassLoader` to be added as class loader.
    // It results in throwing "java.lang.NoClassDefFoundError: org/jetbrains/kotlin/gradle/plugin/KotlinPluginWrapper`.
    //
    // We therefore use `implementation` as a workaround and bundle the plugin code with "build-logic".
    //
    // Because we use `implementation` and not `compileOnly` only,
    // they must not add any of the Kotlin Gradle plugins again in the applying projects.
    // This means we must not call `alias(libs.plugins.kotlin.multiplatform)`
    // and `alias(libs.plugins.kotlin.jvm), which tries to add them again as plugins.
    // We just have to call `kotlin("multiplatform")` and `kotlin("jvm")` which just enables the plugins,
    // but uses the plugin code bundled with `build-logic`.
    implementation(libs.kotlin.gradlePlugin)
}
