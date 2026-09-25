package org.modelix

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// We only support MPS 2024.1+, which runs on JDK 17.
const val MODELIX_JDK_VERSION = 17
val MODELIX_JVM_TARGET = JvmTarget.JVM_17
// MPS 2024.1 bundles the Kotlin stdlib 1.9, and the plugins run with the stdlib bundled with MPS.
val MODELIX_KOTLIN_API_VERSION = KotlinVersion.KOTLIN_1_9
