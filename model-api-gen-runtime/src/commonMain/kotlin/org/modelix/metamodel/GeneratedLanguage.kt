/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.metamodel

import org.modelix.model.api.ILanguage

abstract class GeneratedLanguage(private val name: String) : ILanguage {
    fun register() {
        TypedLanguagesRegistry.register(this)
    }

    fun unregister() {
        TypedLanguagesRegistry.unregister(this)
    }

    fun isRegistered(): Boolean {
        return TypedLanguagesRegistry.isRegistered(this)
    }

    fun assertRegistered() {
        if (!isRegistered()) throw IllegalStateException("Language ${getUID()} is not registered")
    }

    override fun getName(): String {
        return name
    }

    override fun getUID(): String {
        return getName()
    }
}
