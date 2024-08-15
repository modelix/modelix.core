/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import gradle.kotlin.dsl.accessors._9d6accdeac6876c73060866945fb6d8c.java
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.modelix.MODELIX_JDK_VERSION
import org.modelix.MODELIX_JVM_TARGET
import org.modelix.MODELIX_KOTLIN_API_VERSION

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(MODELIX_JDK_VERSION))
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

plugins.withType<KotlinPlatformJvmPlugin> {
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
