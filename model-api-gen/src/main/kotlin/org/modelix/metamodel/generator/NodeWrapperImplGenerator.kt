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

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.ChildListAccessor
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.MandatoryReferenceAccessor
import org.modelix.metamodel.OptionalReferenceAccessor
import org.modelix.metamodel.RawPropertyAccessor
import org.modelix.metamodel.RawReferenceAccessor
import org.modelix.metamodel.SingleChildAccessor
import org.modelix.metamodel.TypedNodeImpl
import org.modelix.metamodel.TypedPropertyAccessor
import org.modelix.model.api.INode

internal class NodeWrapperImplGenerator(
    private val concept: ProcessedConcept,
    override val nameConfig: NameConfig,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig) {

    fun generate(): TypeSpec {
        val constructorSpec = FunSpec.constructorBuilder().runBuild {
            addParameter("_node", INode::class)
        }

        return TypeSpec.classBuilder(concept.nodeWrapperImplType()).runBuild {
            addModifiers(KModifier.OPEN)
            addConceptProperty()

            if (concept.extends.size > 1) {
                // fix kotlin warning about ambiguity in case of multiple inheritance
                addUnwrapFunction()
            }
            primaryConstructor(constructorSpec)
            addSuperTypes()
            for (feature in concept.getOwnAndDuplicateRoles()) {
                when (feature) {
                    is ProcessedProperty -> {
                        addRegularProperty(feature)
                        addRawProperty(feature)
                    }

                    is ProcessedChildLink -> addChildLink(feature)

                    is ProcessedReferenceLink -> {
                        addRegularReference(feature)
                        addOrNullReference(feature)
                        addRawReference(feature)
                    }
                }
            }
        }
    }

    private fun TypeSpec.Builder.addRawReference(feature: ProcessedRole) {
        val propertySpec = PropertySpec.builder(
            name = "raw_" + feature.generatedName,
            type = INode::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addModifiers(KModifier.OVERRIDE)
            mutable(true)
            delegate(
                """%T(${ITypedNode::unwrap.name}(), %T.%N)""",
                RawReferenceAccessor::class.asClassName(),
                feature.concept.conceptObjectType(),
                feature.generatedName,
            )
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addOrNullReference(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = referenceLink.generatedName + "_orNull",
            type = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = true),
        ).runBuild {
            addModifiers(KModifier.OVERRIDE)
            mutable(false)
            delegate(
                """%T(%N(), %T.%N, %T::class)""",
                OptionalReferenceAccessor::class.asTypeName(),
                ITypedNode::unwrap.name,
                referenceLink.concept.conceptObjectType(),
                referenceLink.generatedName,
                referenceLink.type.resolved.nodeWrapperInterfaceType(),
            )
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularReference(referenceLink: ProcessedReferenceLink) {
        val accessorClass = if (referenceLink.optional) {
            OptionalReferenceAccessor::class
        } else {
            MandatoryReferenceAccessor::class
        }

        val propertySpec = PropertySpec.builder(
            name = referenceLink.generatedName,
            type = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = referenceLink.optional),
        ).runBuild {
            addModifiers(KModifier.OVERRIDE)
            mutable(true)
            delegate(
                """%T(%N(), %T.%N, %T::class)""",
                accessorClass.asTypeName(),
                ITypedNode::unwrap.name,
                referenceLink.concept.conceptObjectType(),
                referenceLink.generatedName,
                referenceLink.type.resolved.nodeWrapperInterfaceType(),
            )
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
            addModifiers(KModifier.OVERRIDE)
            initializer(
                """%T(%N(), %T.%N, %T, %T::class)""",
                accessorSubclass.asTypeName(),
                ITypedNode::unwrap.name,
                childLink.concept.conceptObjectType(),
                childLink.generatedName,
                childLink.type.resolved.conceptObjectType(),
                childLink.type.resolved.nodeWrapperInterfaceType(),
            )
        }
        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRawProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            "raw_" + property.generatedName,
            String::class.asTypeName().copy(nullable = true),
        ).runBuild {
            addModifiers(KModifier.OVERRIDE)
            mutable(true)
            delegate(
                """%T(unwrap(), %T.%N.untyped())""",
                RawPropertyAccessor::class.asTypeName(),
                property.concept.conceptObjectType(),
                property.generatedName,
            )
        }
        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addRegularProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = property.generatedName,
            type = property.asKotlinType(alwaysUseNonNullableProperties),
        ).runBuild {
            addModifiers(KModifier.OVERRIDE)
            mutable(true)
            delegate(
                """%T(unwrap(), %T.%N)""",
                TypedPropertyAccessor::class.asTypeName(),
                property.concept.conceptObjectType(),
                property.generatedName,
            )
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addSuperTypes() {
        if (concept.extends.isEmpty()) {
            superclass(TypedNodeImpl::class)
            addSuperclassConstructorParameter("_node")
        } else {
            superclass(concept.extends.first().resolved.nodeWrapperImplType())
            addSuperclassConstructorParameter("_node")
            for (extended in concept.extends.drop(1)) {
                addSuperinterface(
                    extended.resolved.nodeWrapperInterfaceType(),
                    CodeBlock.of("%T(_node)", extended.resolved.nodeWrapperImplType()),
                )
            }
        }
        addSuperinterface(concept.nodeWrapperInterfaceType())
    }

    private fun TypeSpec.Builder.addUnwrapFunction() {
        val funSpec = FunSpec.builder(ITypedNode::unwrap.name).runBuild {
            addModifiers(KModifier.OVERRIDE)
            returns(INode::class)
            addStatement("return " + TypedNodeImpl::wrappedNode.name)
        }

        addFunction(funSpec)
    }

    private fun TypeSpec.Builder.addConceptProperty() {
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("""return %T""", concept.conceptWrapperInterfaceClass())
        }

        val propertySpec = PropertySpec.builder(
            TypedNodeImpl::_concept.name,
            concept.conceptWrapperInterfaceType(),
            KModifier.OVERRIDE,
        ).runBuild {
            getter(getterSpec)
        }

        addProperty(propertySpec)
    }
}
