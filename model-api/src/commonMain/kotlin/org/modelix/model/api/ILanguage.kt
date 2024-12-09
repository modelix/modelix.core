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
