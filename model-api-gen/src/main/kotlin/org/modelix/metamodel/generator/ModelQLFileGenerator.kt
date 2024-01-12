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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.ITypedConcept
import org.modelix.model.data.EnumPropertyType
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.typed.TypedModelQL
import java.nio.file.Path

internal class ModelQLFileGenerator(
    private val concept: ProcessedConcept,
    private val outputDir: Path,
    override val nameConfig: NameConfig,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig) {

    companion object {
        private const val PACKAGE_PREFIX = "org.modelix.modelql.gen."
    }

    fun generateFile() {
        buildModelQLFileSpec().writeTo(outputDir)
    }

    private fun buildModelQLFileSpec(): FileSpec {
        return FileSpec.builder(PACKAGE_PREFIX + concept.language.name, concept.name).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            for (feature in concept.getOwnRoles()) {
                when (feature) {
                    is ProcessedProperty -> {
                        addPropertyGetterForStepType(feature, IMonoStep::class.asTypeName())
                        addPropertyGetterForStepType(feature, IFluxStep::class.asTypeName())
                        addPropertySetter(feature)
                    }

                    is ProcessedChildLink -> {
                        addChildGetter(feature)
                        addChildSetter(feature)
                    }

                    is ProcessedReferenceLink -> {
                        addReferenceGettersForStepType(feature, IMonoStep::class.asTypeName())
                        addReferenceGettersForStepType(feature, IFluxStep::class.asTypeName())
                        addReferenceSetter(feature)
                    }
                }
            }
        }
    }

    private fun FileSpec.Builder.addReferenceSetter(referenceLink: ProcessedReferenceLink) {
        val targetType = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = referenceLink.optional)
        val inputStepType = IMonoStep::class.asTypeName().parameterizedBy(concept.nodeWrapperInterfaceType())

        val parameterType = IMonoStep::class.asTypeName().parameterizedBy(targetType)
            .let { if (referenceLink.optional) it.copy(nullable = true) else it }

        val funSpec = FunSpec.builder(referenceLink.setterName()).runBuild {
            returns(inputStepType)
            receiver(inputStepType)
            addParameter("target", parameterType)

            addStatement(
                "return %T.setReference(this, %T.%N, target)",
                TypedModelQL::class.asTypeName(),
                concept.conceptWrapperInterfaceClass(),
                referenceLink.generatedName,
            )
        }

        addFunction(funSpec)
    }

    private fun FileSpec.Builder.addReferenceGettersForStepType(
        referenceLink: ProcessedReferenceLink,
        stepType: ClassName,
    ) {
        val targetType = referenceLink.type.resolved.nodeWrapperInterfaceType().copy(nullable = referenceLink.optional)
        val inputType = stepType.parameterizedBy(concept.nodeWrapperInterfaceType())
        val outputType = stepType.parameterizedBy(targetType.copy(nullable = false))
        val outputTypeNullable = stepType.parameterizedBy(targetType.copy(nullable = true))
        addRegularReferenceGetter(referenceLink, inputType, outputType)
        addOrNullReferenceGetter(referenceLink, inputType, outputTypeNullable)
    }

    private fun FileSpec.Builder.addOrNullReferenceGetter(
        referenceLink: ProcessedReferenceLink,
        inputType: ParameterizedTypeName,
        outputType: ParameterizedTypeName,
    ) {
        val getterImpl = FunSpec.getterBuilder().runBuild {
            addStatement(
                "return %T.referenceOrNull(this, %T.%N)",
                TypedModelQL::class.asTypeName(),
                concept.conceptWrapperInterfaceClass(),
                referenceLink.generatedName,
            )
        }

        val propertySpec = PropertySpec.builder(referenceLink.generatedName + "_orNull", outputType).runBuild {
            receiver(inputType)
            getter(getterImpl)
        }

        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addRegularReferenceGetter(
        referenceLink: ProcessedReferenceLink,
        inputType: ParameterizedTypeName,
        outputType: ParameterizedTypeName,
    ) {
        val getterImpl = FunSpec.getterBuilder().runBuild {
            addStatement(
                "return %T.reference(this, %T.%N)",
                TypedModelQL::class.asTypeName(),
                concept.conceptWrapperInterfaceClass(),
                referenceLink.generatedName,
            )
        }

        val propertySpec = PropertySpec.builder(referenceLink.generatedName, outputType).runBuild {
            receiver(inputType)
            getter(getterImpl)
        }

        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addChildSetter(childLink: ProcessedChildLink) {
        val targetType = childLink.type.resolved.nodeWrapperInterfaceType()
        val returnType = IMonoStep::class.asTypeName().parameterizedBy(targetType)
        val receiverType = IMonoStep::class.asTypeName().parameterizedBy(concept.nodeWrapperInterfaceType())
        val conceptParameter = ParameterSpec.builder("concept", ITypedConcept::class.asTypeName()).apply {
            if (!childLink.type.resolved.abstract) {
                defaultValue("%T", childLink.type.resolved.conceptWrapperInterfaceClass())
            }
        }.build()

        val funName = if (childLink.multiple) childLink.adderMethodName() else childLink.setterName()

        val funSpec = FunSpec.builder(funName).runBuild {
            returns(returnType)
            receiver(receiverType)
            addParameter(conceptParameter)
            if (childLink.multiple) {
                val indexParameter = ParameterSpec.builder("index", Int::class.asTypeName())
                    .defaultValue("-1")
                    .build()

                addParameter(indexParameter)
                addStatement(
                    "return %T.addNewChild(this, %T.%N, index, concept)",
                    TypedModelQL::class.asTypeName(),
                    concept.conceptObjectType(),
                    childLink.generatedName,
                )
            } else {
                addStatement(
                    "return %T.setChild(this, %T.%N, concept)",
                    TypedModelQL::class.asTypeName(),
                    concept.conceptObjectType(),
                    childLink.generatedName,
                )
            }
        }

        addFunction(funSpec)
    }

    private fun FileSpec.Builder.addChildGetter(childLink: ProcessedChildLink) {
        val inputStepType = (if (childLink.multiple) IProducingStep::class else IMonoStep::class).asTypeName()
        val outputStepType = (if (childLink.multiple) IFluxStep::class else IMonoStep::class).asTypeName()

        val inputType = inputStepType.parameterizedBy(concept.nodeWrapperInterfaceType())
        val isOptionalSingle = childLink.optional && !childLink.multiple
        val targetType = childLink.type.resolved.nodeWrapperInterfaceType()
        val outputType = outputStepType.parameterizedBy(targetType.copy(nullable = isOptionalSingle))

        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement(
                "return %T.children(this, %T.%N)",
                TypedModelQL::class.asTypeName(),
                concept.conceptWrapperInterfaceClass(),
                childLink.generatedName,
            )
        }

        val propertySpec = PropertySpec.builder(childLink.generatedName, outputType).runBuild {
            receiver(inputType)
            getter(getterSpec)
        }

        addProperty(propertySpec)
    }

    private fun FileSpec.Builder.addPropertySetter(property: ProcessedProperty) {
        val inputStepType = IMonoStep::class.asTypeName()
            .parameterizedBy(concept.nodeWrapperInterfaceType())

        val parameterType = IMonoStep::class.asTypeName()
            .parameterizedBy(property.asKotlinType(alwaysUseNonNullableProperties))

        val setterSpec = FunSpec.builder(property.setterName()).runBuild {
            returns(inputStepType)
            receiver(inputStepType)
            addParameter("value", parameterType)
            addStatement(
                "return %T.setProperty(this, %T.%N, value)",
                TypedModelQL::class.asTypeName(),
                concept.conceptWrapperInterfaceClass(),
                property.generatedName,
            )
        }

        addFunction(setterSpec)
    }

    private fun FileSpec.Builder.addPropertyGetterForStepType(
        property: ProcessedProperty,
        stepType: ClassName,
    ) {
        val inputType = stepType.parameterizedBy(concept.nodeWrapperInterfaceType())
        val outputElementType = when (property.type) {
            is EnumPropertyType -> String::class.asTypeName().copy(nullable = true)
            is PrimitivePropertyType -> property.asKotlinType(alwaysUseNonNullableProperties)
        }
        val outputType = stepType.parameterizedBy(outputElementType)
        val functionName = when (val type = property.type) {
            is EnumPropertyType -> "rawProperty"
            is PrimitivePropertyType -> when (type.primitive) {
                Primitive.STRING -> "stringProperty"
                Primitive.BOOLEAN -> "booleanProperty"
                Primitive.INT -> "intProperty"
            }
        }
        val getterSpec = FunSpec.getterBuilder().runBuild {
            addStatement(
                "return %T.%N(this, %T.%N)",
                TypedModelQL::class.asTypeName(),
                functionName,
                concept.conceptWrapperInterfaceClass(),
                property.generatedName,
            )
        }

        val propertySpec = PropertySpec.builder(property.generatedName, outputType).runBuild {
            receiver(inputType)
            getter(getterSpec)
        }

        addProperty(propertySpec)
    }
}
