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

package org.modelix.model.persistent

/**
 * To make the serialization format more concise, readable and easy to parse we just escape all data using URL encode
 * and use different separators on each level of nested objects. Splitting the string at the separators is all we need
 * to parse the data.
 *
 * Each of the separators should be unique and not appear in the output of SerializationUtil.escape (URL encode)
 */
object Separators {
    /** Between parts of top level elements stored in the key-value store: CPVersion, CPNode, CPHamt* */
    const val LEVEL1 = "/"

    /** Between elements of the operation list */
    const val LEVEL2 = ","

    const val LEVEL3 = ";"

    /** For lists inside operation parts */
    const val LEVEL4 = ":" //

    /** For properties and references */
    const val MAPPING = "="

    const val OPS = LEVEL2
    const val OP_PARTS = LEVEL3
}
