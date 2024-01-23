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

pluginManagement {
    val modelixCoreVersion: String = file("../version.txt").readText()
    val modelixRegex = "org\\.modelix.*"
    plugins {
        id("org.modelix.bulk-model-sync") version modelixCoreVersion
        id("org.modelix.model-api-gen") version modelixCoreVersion
    }
    repositories {
        mavenLocal {
            content {
                includeGroupByRegex(modelixRegex)
            }
        }
        gradlePluginPortal {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
        maven {
            url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
            content {
                includeGroupByRegex(modelixRegex)
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
    }
    dependencyResolutionManagement {
        repositories {
            mavenLocal {
                content {
                    includeGroupByRegex(modelixRegex)
                }
            }
            gradlePluginPortal {
                content {
                    excludeGroupByRegex(modelixRegex)
                }
            }
            maven {
                url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
                content {
                    includeGroupByRegex(modelixRegex)
                    includeGroup("com.jetbrains")
                }
            }
            mavenCentral {
                content {
                    excludeGroupByRegex(modelixRegex)
                }
            }
        }
        versionCatalogs {
            create("libs") {
                from("org.modelix:core-version-catalog:$modelixCoreVersion")
            }
        }
    }
}
