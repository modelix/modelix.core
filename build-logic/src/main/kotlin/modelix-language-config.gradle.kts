import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.modelix.MODELIX_JDK_VERSION
import org.modelix.MODELIX_JVM_TARGET
import org.modelix.MODELIX_KOTLIN_API_VERSION

plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(MODELIX_JDK_VERSION))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        this.compilerOptions {
            jvmTarget.set(MODELIX_JVM_TARGET)
            freeCompilerArgs.addAll(listOf("-Xjvm-default=all-compatibility", "-Xexpect-actual-classes"))
            apiVersion.set(MODELIX_KOTLIN_API_VERSION)
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        this.compilerOptions {
            jvmTarget.set(MODELIX_JVM_TARGET)
            freeCompilerArgs.addAll(listOf("-Xjvm-default=all-compatibility"))
            apiVersion.set(MODELIX_KOTLIN_API_VERSION)
        }
    }
}

plugins.withType<KotlinPluginWrapper> {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(MODELIX_JDK_VERSION)
        compilerOptions {
            jvmTarget.set(MODELIX_JVM_TARGET)
        }
    }
}

plugins.withType<KotlinMultiplatformPluginWrapper> {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(MODELIX_JDK_VERSION)
        sourceSets.all {
            if (!name.lowercase().contains("test")) {
                languageSettings {
                    apiVersion = MODELIX_KOTLIN_API_VERSION.version
                }
            }
        }
    }
}
