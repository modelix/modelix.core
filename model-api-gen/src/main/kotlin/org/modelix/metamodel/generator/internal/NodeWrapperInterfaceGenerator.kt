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

package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.ChildListAccessor
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.SingleChildAccessor
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedChildLink
import org.modelix.metamodel.generator.ProcessedConcept
import org.modelix.metamodel.generator.ProcessedProperty
import org.modelix.metamodel.generator.ProcessedReferenceLink
import org.modelix.metamodel.generator.addDeprecationIfNecessary
import org.modelix.metamodel.generator.runBuild
import org.modelix.model.api.INode

internal class NodeWrapperInterfaceGenerator(
    private val concept: ProcessedConcept,
    override val nameConfig: NameConfig,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig) {

    fun generate(): TypeSpec {
        return TypeSpec.interfaceBuilder(concept.nodeWrapperInterfaceType()).runBuild {
            addDeprecationIfNecessary(concept)
            addSuperInterfaces()
            for (feature in concept.getOwnRoles()) {
                when (feature) {
                    is ProcessedProperty -> {
                        addRegularProperty(feature)
                        addRawProperty(feature)
                    }
                    is ProcessedChildLink -> addChildLink(feature)
                    is ProcessedReferenceLink -> {
                        addRegularReference(feature)
                        addOrNullRefernce(feature)
                        addRawReference(feature)
                    }
                }
            }
        }
    }

    private fun TypeSpec.Builder.addSuperInterfaces() {
        if (concept.extends.isEmpty()) {
            addSuperinterface(ITypedNode::class.asTypeName())
        }

        for (extended in concept.extends) {
            addSuperinterface(extended.resolved.nodeWrapperInterfaceType())
        }
    }

    private fun TypeSpec.Builder.addRawReference(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = "raw_" + referenceLink.generatedName,
            type = INode::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addDeprecationIfNecessary(referenceLink)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addOrNullRefernce(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = referenceLink.generatedName + "_orNull",
            type = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = true),
        ).runBuild {
            mutable(false)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularReference(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = referenceLink.generatedName,
            type = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = referenceLink.optional),
        ).runBuild {
            addDeprecationIfNecessary(referenceLink)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addChildLink(childLink: ProcessedChildLink) {
        // TODO resolve link.type and ensure it exists
        val accessorSubclass = when {
            childLink.multiple -> ChildListAccessor::class
            else -> SingleChildAccessor::class
        }

        val type = accessorSubclass.asClassName().parameterizedBy(childLink.type.resolved.nodeWrapperInterfaceType())

        val propertySpec = PropertySpec.builder(childLink.generatedName, type).runBuild {
            addDeprecationIfNecessary(childLink)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRawProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = "raw_" + property.generatedName,
            type = String::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addDeprecationIfNecessary(property)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = property.generatedName,
            type = property.asKotlinType(alwaysUseNonNullableProperties),
        ).runBuild {
            addDeprecationIfNecessary(property)
            mutable(true)
        }

        addProperty(propertySpec)
    }
}
