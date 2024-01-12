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

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.model.api.INode
import java.nio.file.Path

internal class ConceptFileGenerator(
    private val concept: ProcessedConcept,
    private val outputDir: Path,
    override val nameConfig: NameConfig,
    private val conceptPropertiesInferfaceName: String?,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig) {

    fun generateFile() {
        val conceptObject = ConceptObjectGenerator(concept, nameConfig).generate()
        val conceptWrapperInterface = ConceptWrapperInterfaceGenerator(
            concept,
            nameConfig,
            conceptPropertiesInferfaceName,
            alwaysUseNonNullableProperties,
        ).generate()
        val nodeWrapperInterface = NodeWrapperInterfaceGenerator(concept, nameConfig, alwaysUseNonNullableProperties).generate()
        val nodeWrapperImpl = NodeWrapperImplGenerator(concept, nameConfig, alwaysUseNonNullableProperties).generate()

        val typeAliasSpec = TypeAliasSpec.builder(concept.conceptTypeAliasName(), concept.conceptWrapperInterfaceType()).build()

        FileSpec.builder(concept.language.name, concept.name).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addType(conceptObject)
            addTypeAlias(typeAliasSpec)
            addType(conceptWrapperInterface)
            addType(nodeWrapperInterface)
            addType(nodeWrapperImpl)
            addConceptFeatureShortcuts()
        }.writeTo(outputDir)
    }

    private fun FileSpec.Builder.addConceptFeatureShortcuts() {
        // allow to write `nodes.myChildren` instead of `nodes.flatMap { it.myChildren }`
        for (feature in concept.getOwnRoles()) {
            val receiverType = Iterable::class.asTypeName().parameterizedBy(concept.nodeWrapperInterfaceType())
            when (feature) {
                is ProcessedProperty -> {
                    addRegularConceptProperty(feature, receiverType)
                    addRawConceptProperty(feature, receiverType)
                }
                is ProcessedChildLink -> addConceptChildLink(feature, receiverType)
                is ProcessedReferenceLink -> addConceptReferences(feature, receiverType)
            }
        }
    }

    private fun FileSpec.Builder.addConceptReferences(
        feature: ProcessedReferenceLink,
        receiverType: ParameterizedTypeName,
    ) {
        val targetType = feature.type.resolved.nodeWrapperInterfaceType().copy(nullable = feature.optional)
        val rawTargetType = INode::class.asTypeName().copy(nullable = true)

        addRegularConceptReference(feature, targetType, receiverType)
        addOrNullConceptReference(feature, targetType, receiverType)
        addRawConceptReference(feature, rawTargetType, receiverType)
    }

    private fun FileSpec.Builder.addRawConceptReference(
        feature: ProcessedRole,
        rawTargetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(rawTargetType)
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", "raw_" + feature.generatedName)
        }

        val propertySpec = PropertySpec.builder("raw_" + feature.generatedName, refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addOrNullConceptReference(
        feature: ProcessedRole,
        targetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(targetType.copy(nullable = true))

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", feature.generatedName + "_orNull")
        }

        val propertySpec = PropertySpec.builder(feature.generatedName + "_orNull", refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRegularConceptReference(
        feature: ProcessedRole,
        targetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(targetType)

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", feature.generatedName)
        }

        val propertySpec = PropertySpec.builder(feature.generatedName, refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addConceptChildLink(
        feature: ProcessedChildLink,
        receiverType: ParameterizedTypeName,
    ) {
        val targetType = feature.type.resolved.nodeWrapperInterfaceType()
        val returnType = List::class.asTypeName().parameterizedBy(targetType)
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return flatMap { it.%N }", feature.generatedName)
        }

        val propertySpec = PropertySpec.builder(feature.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRawConceptProperty(
        feature: ProcessedProperty,
        receiverType: ParameterizedTypeName,
    ) {
        val returnType = List::class.asTypeName().parameterizedBy(String::class.asTypeName().copy(nullable = true))

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", "raw_" + feature.generatedName)
        }

        val propertySpec = PropertySpec.builder("raw_" + feature.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRegularConceptProperty(
        feature: ProcessedProperty,
        receiverType: ParameterizedTypeName,
    ) {
        val returnType = List::class.asTypeName().parameterizedBy(feature.asKotlinType(alwaysUseNonNullableProperties))
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", feature.generatedName)
        }
        val propertySpec = PropertySpec.builder(feature.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }
}
