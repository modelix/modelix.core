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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.GeneratedProperty
import org.modelix.metamodel.MandatoryBooleanPropertySerializer
import org.modelix.metamodel.MandatoryEnumSerializer
import org.modelix.metamodel.MandatoryIntPropertySerializer
import org.modelix.metamodel.MandatoryStringPropertySerializer
import org.modelix.metamodel.OptionalBooleanPropertySerializer
import org.modelix.metamodel.OptionalEnumSerializer
import org.modelix.metamodel.OptionalIntPropertySerializer
import org.modelix.metamodel.OptionalStringPropertySerializer
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedChildLink
import org.modelix.metamodel.generator.ProcessedConcept
import org.modelix.metamodel.generator.ProcessedProperty
import org.modelix.metamodel.generator.ProcessedReferenceLink
import org.modelix.metamodel.generator.runBuild
import org.modelix.metamodel.generator.toListLiteralCodeBlock
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage
import org.modelix.model.api.INode
import org.modelix.model.data.EnumPropertyType
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import kotlin.reflect.KClass

internal class ConceptObjectGenerator(
    private val concept: ProcessedConcept,
    override val nameConfig: NameConfig,
    private val alwaysUseNonNullableProperties: Boolean = true,
) : NameConfigBasedGenerator(nameConfig) {

    fun generate(): TypeSpec {
        val superclassType = GeneratedConcept::class.asTypeName().parameterizedBy(
            concept.nodeWrapperInterfaceType(),
            concept.conceptWrapperInterfaceType(),
        )
        return TypeSpec.objectBuilder(concept.conceptObjectName()).runBuild {
            superclass(superclassType)
            addSuperclassConstructorParameter("%S", concept.name)
            addSuperclassConstructorParameter(concept.abstract.toString())
            addInstanceClassGetter()
            addTypedFun()
            addConceptPropertiesGetter()
            addLanguageProperty()
            addWrapFun()
            if (concept.uid != null) {
                addUidGetter()
            }
            addDirectSuperConceptsGetter()
            for (feature in concept.getOwnRoles()) {
                when (feature) {
                    is ProcessedProperty -> addConceptObjectProperty(feature)
                    is ProcessedChildLink -> addConceptObjectChildLink(feature)
                    is ProcessedReferenceLink -> addConceptObjectReferenceLink(feature)
                }
            }
        }
    }

    private fun TypeSpec.Builder.addConceptObjectReferenceLink(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(
            name = referenceLink.generatedName,
            type = referenceLink.generatedReferenceLinkType(),
        ).runBuild {
            initializer(
                """newReferenceLink(%S, %S, ${referenceLink.optional}, %T, %T::class)""",
                referenceLink.originalName,
                referenceLink.uid,
                referenceLink.type.resolved.conceptObjectType(),
                referenceLink.type.resolved.nodeWrapperInterfaceType(),
            )
        }
        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptPropertiesGetter() {
        if (concept.metaProperties.isEmpty()) return

        val getConceptPropertyFun = FunSpec.builder(GeneratedConcept<*, *>::getConceptProperty.name).runBuild {
            val paramName = GeneratedConcept<*, *>::getConceptProperty.parameters.first().name ?: "name"
            returns(String::class.asTypeName().copy(nullable = true))
            addParameter(paramName, String::class)
            addModifiers(KModifier.OVERRIDE)
            beginControlFlow("return when (%N)", paramName)
            for ((key, _) in concept.metaProperties) {
                addStatement("""%S -> %T.%N""", key, concept.conceptWrapperInterfaceClass(), key)
            }
            addStatement("else -> null")
            endControlFlow()
        }

        addFunction(getConceptPropertyFun)
    }

    private fun TypeSpec.Builder.addConceptObjectChildLink(childLink: ProcessedChildLink) {
        val methodName = if (childLink.multiple) {
            "newChildListLink"
        } else {
            if (childLink.optional) {
                "newOptionalSingleChildLink"
            } else {
                "newMandatorySingleChildLink"
            }
        }

        val propertySpec = PropertySpec.builder(childLink.generatedName, childLink.generatedChildLinkType()).runBuild {
            initializer(
                """$methodName(%S, %S, ${if (childLink.multiple) "${childLink.optional}, " else ""}%T, %T::class)""",
                childLink.originalName,
                childLink.uid,
                childLink.type.resolved.conceptObjectType(),
                childLink.type.resolved.nodeWrapperInterfaceType(),
            )
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptObjectProperty(property: ProcessedProperty) {
        val serializer = getSerializerTypeName(property)

        val propertyType = GeneratedProperty::class.asClassName()
            .parameterizedBy(property.asKotlinType(alwaysUseNonNullableProperties))

        val propertySpec = PropertySpec.builder(property.generatedName, propertyType).runBuild {
            addConceptPropertyInitializer(property, serializer)
        }
        addProperty(propertySpec)
    }

    private fun PropertySpec.Builder.addConceptPropertyInitializer(
        property: ProcessedProperty,
        serializer: ClassName,
    ) {
        if (property.type is EnumPropertyType) {
            if (serializer == MandatoryEnumSerializer::class.asTypeName()) {
                initializer(
                    """newProperty(%S, %S, %T({ it.uid },
                                                    |{ if (it != null) %T.getLiteralByMemberId(it) else %T.defaultValue() }),
                                                    |${property.optional})
                    """.trimMargin(),
                    property.originalName,
                    property.uid,
                    serializer,
                    property.asKotlinType(alwaysUseNonNullableProperties),
                    property.asKotlinType(alwaysUseNonNullableProperties),
                )
            } else {
                initializer(
                    """newProperty(%S, %S, %T( { it.uid }, { %T.getLiteralByMemberId(it) }),
                                                    |${property.optional})
                    """.trimMargin(),
                    property.originalName,
                    property.uid,
                    serializer,
                    property.asKotlinType(alwaysUseNonNullableProperties),
                )
            }
        } else {
            initializer(
                """newProperty(%S, %S, %T, ${property.optional})""",
                property.originalName,
                property.uid,
                serializer,
            )
        }
    }

    private fun getSerializerTypeName(property: ProcessedProperty) = (
        if (!property.optional || alwaysUseNonNullableProperties) {
            when (property.type) {
                is PrimitivePropertyType -> when ((property.type as PrimitivePropertyType).primitive) {
                    Primitive.STRING -> MandatoryStringPropertySerializer::class
                    Primitive.BOOLEAN -> MandatoryBooleanPropertySerializer::class
                    Primitive.INT -> MandatoryIntPropertySerializer::class
                }

                is EnumPropertyType -> MandatoryEnumSerializer::class
                else -> throw RuntimeException("Unexpected property type: ${property.type}")
            }
        } else {
            when (property.type) {
                is PrimitivePropertyType -> when ((property.type as PrimitivePropertyType).primitive) {
                    Primitive.STRING -> OptionalStringPropertySerializer::class
                    Primitive.BOOLEAN -> OptionalBooleanPropertySerializer::class
                    Primitive.INT -> OptionalIntPropertySerializer::class
                }

                is EnumPropertyType -> OptionalEnumSerializer::class
                else -> throw RuntimeException("Unexpected property type: ${property.type}")
            }
        }
        ).asTypeName()

    private fun TypeSpec.Builder.addDirectSuperConceptsGetter() {
        val returnType = List::class.asTypeName().parameterizedBy(IConcept::class.asTypeName())
        val code = concept.getDirectSuperConcepts().map { it.conceptObjectType() }.toList().toListLiteralCodeBlock()

        val funSpec = FunSpec.builder(GeneratedConcept<*, *>::getDirectSuperConcepts.name).runBuild {
            addModifiers(KModifier.OVERRIDE)
            addCode(code)
            returns(returnType)
        }

        addFunction(funSpec)
    }

    private fun TypeSpec.Builder.addUidGetter() {
        val funSpec = FunSpec.builder(GeneratedConcept<*, *>::getUID.name).runBuild {
            returns(String::class)
            addModifiers(KModifier.OVERRIDE)
            addStatement(CodeBlock.of("return %S", concept.uid).toString())
        }

        addFunction(funSpec)
    }

    private fun TypeSpec.Builder.addWrapFun() {
        val funSpec = FunSpec.builder(GeneratedConcept<*, *>::wrap.name).runBuild {
            returns(concept.nodeWrapperImplType())
            addModifiers(KModifier.OVERRIDE)
            addParameter("node", INode::class)
            addStatement("return %T(node)", concept.nodeWrapperImplType())
        }
        addFunction(funSpec)
    }

    private fun TypeSpec.Builder.addLanguageProperty() {
        val propertySpec = PropertySpec.builder(IConcept::language.name, ILanguage::class, KModifier.OVERRIDE).runBuild {
            initializer(concept.language.generatedClassName().simpleName)
        }
        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addTypedFun() {
        val funSpec = FunSpec.builder(GeneratedConcept<*, *>::typed.name).runBuild {
            returns(concept.conceptWrapperInterfaceType())
            addModifiers(KModifier.OVERRIDE)
            addStatement("""return %T""", concept.conceptWrapperInterfaceClass())
        }
        addFunction(funSpec)
    }

    private fun TypeSpec.Builder.addInstanceClassGetter() {
        val instanceClassType = KClass::class.asClassName().parameterizedBy(concept.nodeWrapperImplType())

        val funSpec = FunSpec.builder(GeneratedConcept<*, *>::getInstanceClass.name).runBuild {
            returns(instanceClassType)
            addModifiers(KModifier.OVERRIDE)
            addStatement("""return %T::class""", concept.nodeWrapperImplType())
        }
        addFunction(funSpec)
    }
}
