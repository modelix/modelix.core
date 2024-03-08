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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.GeneratedChildListLink
import org.modelix.metamodel.GeneratedMandatorySingleChildLink
import org.modelix.metamodel.GeneratedReferenceLink
import org.modelix.metamodel.GeneratedSingleChildLink
import org.modelix.metamodel.ITypedChildListLink
import org.modelix.metamodel.ITypedMandatorySingleChildLink
import org.modelix.metamodel.ITypedProperty
import org.modelix.metamodel.ITypedReferenceLink
import org.modelix.metamodel.ITypedSingleChildLink
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedChildLink
import org.modelix.metamodel.generator.ProcessedConcept
import org.modelix.metamodel.generator.ProcessedLanguage
import org.modelix.metamodel.generator.ProcessedProperty
import org.modelix.metamodel.generator.ProcessedReferenceLink
import org.modelix.metamodel.generator.ProcessedRole
import org.modelix.model.data.EnumPropertyType
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType

/**
 * Base class for generators using [NameConfig].
 * Acts as single source of truth for functions needed in multiple internal generators.
 */
internal abstract class NameConfigBasedGenerator(open val nameConfig: NameConfig) {
    protected fun ProcessedConcept.conceptWrapperInterfaceType() =
        conceptWrapperInterfaceClass().parameterizedBy(nodeWrapperInterfaceType())

    protected fun ProcessedConcept.conceptWrapperInterfaceClass() =
        ClassName(language.name, nameConfig.typedConcept(name))

    protected fun ProcessedLanguage.generatedClassName() = ClassName(name, nameConfig.languageClass(name))
    private fun ProcessedConcept.nodeWrapperInterfaceName() = nameConfig.typedNode(name)
    private fun ProcessedConcept.nodeWrapperImplName() = nameConfig.typedNodeImpl(name)
    protected fun ProcessedConcept.conceptObjectName() = nameConfig.untypedConcept(name)
    protected fun ProcessedConcept.conceptTypeAliasName() = nameConfig.conceptTypeAlias(name)
    // protected fun ProcessedConcept.conceptWrapperImplName() = nameConfig.conceptWrapperImplName(name)
    // protected fun ProcessedConcept.conceptWrapperInterfaceName() = nameConfig.conceptWrapperInterfaceName(name)

    // protected fun ProcessedConcept.getConceptFqName() = language.name + "." + name
    protected fun ProcessedConcept.conceptObjectType() = ClassName(language.name, conceptObjectName())
    protected fun ProcessedConcept.nodeWrapperImplType() = ClassName(language.name, nodeWrapperImplName())
    protected fun ProcessedConcept.nodeWrapperInterfaceType() = ClassName(language.name, nodeWrapperInterfaceName())

    // protected fun ProcessedRole.kotlinRef() = CodeBlock.of("%T.%N", concept.conceptObjectType(), generatedName)
    protected fun ProcessedRole.returnKotlinRef() = CodeBlock.of("return %T.%N", concept.conceptObjectType(), generatedName)

    protected fun ProcessedChildLink.generatedChildLinkType(): TypeName {
        val childConcept = type.resolved
        val linkClass = if (multiple) {
            GeneratedChildListLink::class
        } else {
            if (optional) GeneratedSingleChildLink::class else GeneratedMandatorySingleChildLink::class
        }
        return linkClass.asClassName().parameterizedBy(
            childConcept.nodeWrapperInterfaceType(),
            childConcept.conceptWrapperInterfaceType(),
        )
    }

    protected fun ProcessedChildLink.typedLinkType(): TypeName {
        val childConcept = type.resolved
        val linkClass = if (multiple) {
            ITypedChildListLink::class
        } else {
            if (optional) ITypedSingleChildLink::class else ITypedMandatorySingleChildLink::class
        }
        return linkClass.asClassName().parameterizedBy(
            childConcept.nodeWrapperInterfaceType(),
        )
    }

    protected fun ProcessedReferenceLink.generatedReferenceLinkType(): TypeName {
        val targetConcept = type.resolved
        return GeneratedReferenceLink::class.asClassName().parameterizedBy(
            targetConcept.nodeWrapperInterfaceType(),
            targetConcept.conceptWrapperInterfaceType(),
        )
    }

    protected fun ProcessedReferenceLink.typedLinkType(): TypeName {
        val targetConcept = type.resolved
        return ITypedReferenceLink::class.asClassName().parameterizedBy(
            targetConcept.nodeWrapperInterfaceType(),
        )
    }

    protected fun ProcessedProperty.typedPropertyType(alwaysUseNonNullableProperties: Boolean): TypeName {
        return ITypedProperty::class.asClassName().parameterizedBy(
            asKotlinType(alwaysUseNonNullableProperties),
        )
    }

    protected fun ProcessedProperty.asKotlinType(alwaysUseNonNullableProperties: Boolean): TypeName {
        val nonNullableType = when (type) {
            is PrimitivePropertyType -> when ((type as PrimitivePropertyType).primitive) {
                Primitive.STRING -> String::class.asTypeName()
                Primitive.BOOLEAN -> Boolean::class.asTypeName()
                Primitive.INT -> Int::class.asTypeName()
            }
            is EnumPropertyType -> {
                val enumType = (type as EnumPropertyType)
                ClassName(enumType.pckg, enumType.enumName)
            }
            else -> { throw RuntimeException("Unexpected property type: $type") }
        }
        return if (!optional || alwaysUseNonNullableProperties) nonNullableType else nonNullableType.copy(nullable = true)
    }
}
