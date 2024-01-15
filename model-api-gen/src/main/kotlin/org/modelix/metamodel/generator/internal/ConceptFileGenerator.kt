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

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedChildLink
import org.modelix.metamodel.generator.ProcessedConcept
import org.modelix.metamodel.generator.ProcessedProperty
import org.modelix.metamodel.generator.ProcessedReferenceLink
import org.modelix.metamodel.generator.runBuild
import org.modelix.model.api.INode
import java.nio.file.Path

internal class ConceptFileGenerator(
    private val concept: ProcessedConcept,
    override val outputDir: Path,
    override val nameConfig: NameConfig,
    private val conceptPropertiesInterfaceName: String?,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig), FileGenerator {

    override fun generateFileSpec(): FileSpec {
        val conceptObject = ConceptObjectGenerator(concept, nameConfig).generate()
        val conceptWrapperInterface = ConceptWrapperInterfaceGenerator(
            concept,
            nameConfig,
            conceptPropertiesInterfaceName,
            alwaysUseNonNullableProperties,
        ).generate()
        val nodeWrapperInterface = NodeWrapperInterfaceGenerator(concept, nameConfig, alwaysUseNonNullableProperties).generate()
        val nodeWrapperImpl = NodeWrapperImplGenerator(concept, nameConfig, alwaysUseNonNullableProperties).generate()

        val typeAliasSpec = TypeAliasSpec.builder(concept.conceptTypeAliasName(), concept.conceptWrapperInterfaceType()).build()

        return FileSpec.builder(concept.language.name, concept.name).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addType(conceptObject)
            addTypeAlias(typeAliasSpec)
            addType(conceptWrapperInterface)
            addType(nodeWrapperInterface)
            addType(nodeWrapperImpl)
            addConceptFeatureShortcuts()
        }
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
        referenceLink: ProcessedReferenceLink,
        receiverType: ParameterizedTypeName,
    ) {
        val targetType = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = referenceLink.optional)
        val rawTargetType = INode::class.asTypeName().copy(nullable = true)

        addRegularConceptReference(referenceLink, targetType, receiverType)
        addOrNullConceptReference(referenceLink, targetType, receiverType)
        addRawConceptReference(referenceLink, rawTargetType, receiverType)
    }

    private fun FileSpec.Builder.addRawConceptReference(
        referenceLink: ProcessedReferenceLink,
        rawTargetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(rawTargetType)
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", "raw_" + referenceLink.generatedName)
        }

        val propertySpec = PropertySpec.builder("raw_" + referenceLink.generatedName, refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addOrNullConceptReference(
        referenceLink: ProcessedReferenceLink,
        targetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(targetType.copy(nullable = true))

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", referenceLink.generatedName + "_orNull")
        }

        val propertySpec = PropertySpec.builder(referenceLink.generatedName + "_orNull", refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRegularConceptReference(
        referenceLink: ProcessedReferenceLink,
        targetType: TypeName,
        receiverType: ParameterizedTypeName,
    ) {
        val refType = List::class.asTypeName().parameterizedBy(targetType)

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", referenceLink.generatedName)
        }

        val propertySpec = PropertySpec.builder(referenceLink.generatedName, refType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addConceptChildLink(
        childLink: ProcessedChildLink,
        receiverType: ParameterizedTypeName,
    ) {
        val targetType = childLink.type.resolved.nodeWrapperInterfaceType()
        val returnType = List::class.asTypeName().parameterizedBy(targetType)
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return flatMap { it.%N }", childLink.generatedName)
        }

        val propertySpec = PropertySpec.builder(childLink.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRawConceptProperty(
        property: ProcessedProperty,
        receiverType: ParameterizedTypeName,
    ) {
        val returnType = List::class.asTypeName().parameterizedBy(String::class.asTypeName().copy(nullable = true))

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", "raw_" + property.generatedName)
        }

        val propertySpec = PropertySpec.builder("raw_" + property.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRegularConceptProperty(
        property: ProcessedProperty,
        receiverType: ParameterizedTypeName,
    ) {
        val returnType = List::class.asTypeName().parameterizedBy(property.asKotlinType(alwaysUseNonNullableProperties))
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement("return map { it.%N }", property.generatedName)
        }
        val propertySpec = PropertySpec.builder(property.generatedName, returnType).runBuild {
            receiver(receiverType)
            getter(getterSpec)
        }
        addProperty(propertySpec)
    }
}
