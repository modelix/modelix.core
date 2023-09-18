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
package org.modelix.model.client

import org.modelix.model.api.IIdGenerator

expect class IdGenerator private constructor(clientId: Int) : IIdGenerator {
    override fun generate(): Long
    fun generate(quantity: Int): LongRange
    companion object {
        fun getInstance(clientId: Int): IdGenerator
        fun newInstance(clientId: Int): IdGenerator
    }
}
