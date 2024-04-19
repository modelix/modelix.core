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

// For some projects we need to redeclare repositories on project level
// because plugins like npm and intellij may override our settings
repositories {
    val modelixRegex = "org\\.modelix.*"
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
