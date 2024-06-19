/*
 * Copyright (c) 2023.
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

import org.modelix.copyMps

plugins {
    kotlin("jvm")
    // We are not building an actual plugin here.
    // We use/abuse the gradle-intellij-plugin run tests with MPS.
    // (With enough time and effort,
    // one could inspect what the plugin does under the hood
    // and build something custom using the relevant parts.
    // For the time being, this solution works without much overhead and great benefit.)
    alias(libs.plugins.intellij)
    id("modelix-project-repositories")
}

dependencies {
    testImplementation(project(":bulk-model-sync-lib"))
    testImplementation(project(":bulk-model-sync-mps"))
    testImplementation(project(":mps-model-adapters"))
    testImplementation(libs.kotlin.serialization.json)
    testImplementation(libs.xmlunit.matchers)
}

intellij {
    localPath = copyMps().absolutePath
}

tasks {
    // Workaround:
    // * Execution failed for task ':bulk-model-sync-lib-mps-test:buildSearchableOptions'.
    // * > Cannot find IDE platform prefix. Please create a bug report at https://github.com/jetbrains/gradle-intellij-plugin. As a workaround specify `idea.platform.prefix` system property for task `buildSearchableOptions` manually.
    buildSearchableOptions {
        enabled = false
    }
}
