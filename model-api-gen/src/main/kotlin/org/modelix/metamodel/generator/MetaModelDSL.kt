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
package org.modelix.metamodel.generator

import org.modelix.model.data.ChildLinkData
import org.modelix.model.data.ConceptData
import org.modelix.model.data.EnumData
import org.modelix.model.data.LanguageData
import org.modelix.model.data.PropertyData
import org.modelix.model.data.ReferenceLinkData

fun newLanguage(name: String, body: LanguageBuilder.() -> Unit): LanguageData {
    return LanguageBuilder(name).apply(body).build()
}

class LanguageBuilder(val name: String) {
    private val concepts = ArrayList<ConceptData>()
    private val enums = ArrayList<EnumData>()
    fun build(): LanguageData {
        return LanguageData(
            name = name,
            concepts = concepts,
            enums = enums,
        )
    }

    fun concept(name: String, body: ConceptBuilder.() -> Unit = {}) {
        concepts.add(ConceptBuilder(name, this).apply(body).build())
    }
}

class ConceptBuilder(val conceptName: String, val languageBuilder: LanguageBuilder) {
    private var abstract: Boolean = false
    private val properties: MutableList<PropertyData> = ArrayList()
    private val children: MutableList<ChildLinkData> = ArrayList()
    private val references: MutableList<ReferenceLinkData> = ArrayList()
    private val extends: MutableList<String> = ArrayList()

    fun abstract(value: Boolean = true) {
        abstract = value
    }

    fun property(name: String) {
        properties.add(PropertyData(uid = null, name = name))
    }

    fun reference(name: String, type: String, optional: Boolean = false) {
        references.add(ReferenceLinkData(uid = null, name = name, type = type, optional = optional))
    }

    fun optionalReference(name: String, type: String) {
        reference(name, type, true)
    }

    fun child(name: String, type: String, optional: Boolean, multiple: Boolean) {
        children.add(ChildLinkData(name = name, type = type, multiple = multiple, optional = optional))
    }

    fun child0n(name: String, type: String) = child(name = name, type = type, optional = true, multiple = true)
    fun child1n(name: String, type: String) = child(name = name, type = type, optional = false, multiple = true)
    fun child0(name: String, type: String) = child(name = name, type = type, optional = true, multiple = false)
    fun child1(name: String, type: String) = child(name = name, type = type, optional = false, multiple = false)

    fun extends(type: String) {
        extends.add(type)
    }

    fun concept(subConceptName: String, body: ConceptBuilder.() -> Unit = {}) {
        val parentBuilder = this
        languageBuilder.concept(subConceptName) {
            extends(parentBuilder.conceptName)
            body()
        }
    }

    fun build(): ConceptData {
        return ConceptData(
            name = conceptName,
            abstract = abstract,
            properties = properties,
            children = children,
            references = references,
            extends = extends,
        )
    }
}
