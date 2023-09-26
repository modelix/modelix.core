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

package org.modelix.mps.sync.connection

import org.modelix.model.api.ContextValue

// status: ready to test
object AuthorOverride {

    private var AUTHOR = ContextValue<String>()
    private var instanceOwner: String? = null

    fun setInstanceOwner(owner: String?) {
        instanceOwner = owner
    }

    fun apply(author: String?): String? {
        val override = AUTHOR.getValue()
        if (override?.isNotEmpty() == true) {
            return override
        }
        return if (author.isNullOrEmpty() && (instanceOwner?.isNotEmpty() == true)) {
            instanceOwner
        } else {
            author
        }
    }
}
