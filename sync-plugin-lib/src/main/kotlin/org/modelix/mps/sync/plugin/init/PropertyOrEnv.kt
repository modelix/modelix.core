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

package org.modelix.mps.sync.plugin.init

// status: ready to test
object PropertyOrEnv {
    operator fun get(name: String): String? {
        var value = System.getProperty(name)
        if (value.isNullOrEmpty()) {
            value = System.getenv(name)
        }
        if (value.isNullOrEmpty() && name.contains(".")) {
            val withoutDots = name.replace('.', '_')
            value = System.getProperty(withoutDots)
            if (value.isNullOrEmpty()) {
                value = System.getenv(withoutDots)
            }
        }
        return value
    }

    fun getOrElse(name: String, defaultValue: String): String? {
        val value = PropertyOrEnv[name]
        return value?.ifEmpty { defaultValue }
    }

    fun getOrElseBoolean(name: String, defaultValue: Boolean): Boolean {
        val value = PropertyOrEnv[name]
        return if (value?.isNotEmpty() == true) value.toBoolean() else defaultValue
    }
}
