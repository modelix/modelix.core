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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

val kotlinApiVersion = KotlinVersion.KOTLIN_1_6
tasks.withType<KotlinCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        this.compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(listOf("-Xjvm-default=all-compatibility", "-Xexpect-actual-classes"))
            apiVersion.set(kotlinApiVersion)
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        this.compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(listOf("-Xjvm-default=all-compatibility"))
            apiVersion.set(kotlinApiVersion)
        }
    }
}

plugins.withType<KotlinPlatformJvmPlugin> {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(11)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

plugins.withType<KotlinMultiplatformPluginWrapper> {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(11)
        sourceSets.all {
            if (!name.lowercase().contains("test")) {
                languageSettings {
                    apiVersion = kotlinApiVersion.version
                }
            }
        }
    }
}
