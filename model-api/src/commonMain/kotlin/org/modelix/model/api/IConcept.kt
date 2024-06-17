/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.api

/**
 * Representation of a language concept.
 */
interface IConcept {

    /**
     * Returns a reference to this concept.
     *
     * @return concept reference
     */
    fun getReference(): IConceptReference

    /**
     * The language this concept is part of.
     */
    val language: ILanguage?

    /**
     * Returns unique concept id of this concept.
     *
     * @return unique concept id
     */
    fun getUID(): String

    /**
     * Returns the name of this concept.
     *
     * @return short concept name
     */
    fun getShortName(): String

    /**
     * Returns the name of this concept prefixed by the language name.
     *
     * @return long concept name
     */
    fun getLongName(): String

    /**
     * Checks if this concept is abstract.
     *
     * @return true if the concept is abstract, false otherwise
     */
    fun isAbstract(): Boolean

    /**
     * Checks if this concept is a sub-concept of the given concept.
     *
     * @param superConcept the potential super-concept
     * @return true if the given concept is a super-concept of this concept, false otherwise
     */
    fun isSubConceptOf(superConcept: IConcept?): Boolean

    /**
     * Returns the direct super concepts of this concept.
     *
     * @return list of direct super concepts
     */
    fun getDirectSuperConcepts(): List<IConcept>

    /**
     * Checks if the given concept is equal to this concept.
     *
     * @return true if this concept is equal to the given concept, false otherwise
     */
    fun isExactly(concept: IConcept?): Boolean

    /**
     * Returns the properties, which are directly defined in this concept.
     *
     * @return list of own properties
     *
     * @see getAllProperties
     */
    fun getOwnProperties(): List<IProperty>

    /**
     * Returns the child links, which are directly defined in this concept.
     *
     * @return list of own child links
     *
     * @see getAllChildLinks
     */
    fun getOwnChildLinks(): List<IChildLink>

    /**
     * Returns the reference links, which are directly defined in this concept.
     *
     * @return list of reference links
     *
     * @see getAllChildLinks
     */
    fun getOwnReferenceLinks(): List<IReferenceLink>

    /**
     * Returns all properties of this concept.
     *
     * This includes properties, that are directly defined in the concept itself,
     * and properties, which are defined in the super-concepts of this concept.
     *
     * @return list of all properties
     *
     * @see getOwnProperties
     */
    fun getAllProperties(): List<IProperty>

    /**
     * Returns all child links of this concept.
     *
     * This includes child links, that are directly defined in the concept itself,
     * and child links, which are defined in the super-concepts of this concept.
     *
     * @return list of all child links
     *
     * @see getOwnChildLinks
     */
    fun getAllChildLinks(): List<IChildLink>

    /**
     * Returns all reference links of this concepts.
     *
     * This includes reference links, that are directly defined in the concept itself,
     * and reference links, which are defined in the super-concepts of this concept.
     *
     * @return list of all reference links
     *
     * @see getOwnReferenceLinks
     */
    fun getAllReferenceLinks(): List<IReferenceLink>

    /**
     * Returns the property with the given name.
     *
     * @param name name of the property
     * @return property
     */
    fun getProperty(name: String): IProperty

    /**
     * Returns the child link with the given name.
     *
     * @param name name of the child link
     * @return child link
     */
    fun getChildLink(name: String): IChildLink

    /**
     * Returns the reference link with the given name.
     *
     * @param name name of the reference link
     * @return reference link
     */
    fun getReferenceLink(name: String): IReferenceLink

    /**
     * The alias of an MPS concept is one example of a concept property.
     */
    fun getConceptProperty(name: String): String? = null
}

/**
 * @see IConcept.isSubConceptOf
 */
fun IConcept?.isSubConceptOf(superConcept: IConcept?) = this?.isSubConceptOf(superConcept) == true

fun IConcept.conceptAlias() = getConceptProperty("alias")

/**
 * Checks if this is a sub-concept of the [IConcept] that is identified by the [superConceptReference]'s UID.
 *
 * @param superConceptReference a reference to the potential super-concept
 * @return true if this concept (or any of its ancestors) has the same UID as the [superConceptReference]
 */
fun IConcept.isSubConceptOf(superConceptReference: IConceptReference): Boolean {
    if (this.getUID() == superConceptReference.getUID()) {
        return true
    } else {
        for (parent in getDirectSuperConcepts()) {
            if (parent.isSubConceptOf(superConceptReference)) {
                return true
            }
        }
    }
    return false
}
