/*
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
package org.modelix.model.api

/**
 * Representation of a language.
 */
interface ILanguage {
    /**
     * Returns the unique id of this language.
     *
     * @return unique language id
     */
    fun getUID(): String

    /**
     * Returns the name of this language.
     *
     * @return language name
     */
    fun getName(): String

    /**
     * Returns all the concepts defined in this language.
     *
     * @return list of all concepts
     */
    fun getConcepts(): List<IConcept>
}
