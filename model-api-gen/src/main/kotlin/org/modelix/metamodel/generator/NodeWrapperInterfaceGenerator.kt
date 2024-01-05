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

package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.ChildListAccessor
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.SingleChildAccessor
import org.modelix.model.api.INode

internal class NodeWrapperInterfaceGenerator(private val concept: ProcessedConcept, private val generator: MetaModelGenerator) {

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

    private fun TypeSpec.Builder.addRawReference(feature: ProcessedRole) {
        val propertySpec = PropertySpec.builder(
            name = "raw_" + feature.generatedName,
            type = INode::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addDeprecationIfNecessary(feature)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addOrNullRefernce(feature: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = feature.generatedName + "_orNull",
            type = feature.type.resolved.nodeWrapperInterfaceType().copy(nullable = true),
        ).runBuild {
            mutable(false)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularReference(feature: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = feature.generatedName,
            type = feature.type.resolved.nodeWrapperInterfaceType().copy(nullable = feature.optional),
        ).runBuild {
            addDeprecationIfNecessary(feature)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addChildLink(feature: ProcessedChildLink) {
        // TODO resolve link.type and ensure it exists
        val accessorSubclass = when {
            feature.multiple -> ChildListAccessor::class
            else -> SingleChildAccessor::class
        }

        val type = accessorSubclass.asClassName().parameterizedBy(feature.type.resolved.nodeWrapperInterfaceType())

        val propertySpec = PropertySpec.builder(feature.generatedName, type).runBuild {
            addDeprecationIfNecessary(feature)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRawProperty(feature: ProcessedRole) {
        val propertySpec = PropertySpec.builder(
            name = "raw_" + feature.generatedName,
            type = String::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addDeprecationIfNecessary(feature)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularProperty(feature: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = feature.generatedName,
            type = feature.asKotlinType(),
        ).runBuild {
            addDeprecationIfNecessary(feature)
            mutable(true)
        }

        addProperty(propertySpec)
    }

    private fun ProcessedConcept.nodeWrapperInterfaceType() = generator.run { nodeWrapperInterfaceType() }
    private fun ProcessedProperty.asKotlinType() = generator.run { asKotlinType() }
}
