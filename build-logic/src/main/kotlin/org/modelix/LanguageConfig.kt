package org.modelix

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// We only support MPS 2024.1+, which runs on JDK 17.
const val MODELIX_JDK_VERSION = 17
val MODELIX_JVM_TARGET = JvmTarget.JVM_17
val MODELIX_KOTLIN_API_VERSION = KotlinVersion.KOTLIN_1_8
