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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.GeneratedProperty
import org.modelix.metamodel.IConceptOfTypedNode
import org.modelix.metamodel.INonAbstractConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.model.api.IConcept
import kotlin.reflect.KClass

internal class ConceptWrapperInterfaceGenerator(
    private val concept: ProcessedConcept,
    override val nameConfig: NameConfig,
    private val conceptPropertiesInterfaceName: String?,
    private val alwaysUseNonNullableProperties: Boolean,
) : NameConfigBasedGenerator(nameConfig) {

    fun generate(): TypeSpec {
        val nodeT = TypeVariableName("NodeT", concept.nodeWrapperInterfaceType(), variance = KModifier.OUT)

        return TypeSpec.interfaceBuilder(concept.conceptWrapperInterfaceClass()).runBuild {
            addDeprecationIfNecessary(concept)
            addTypeVariable(nodeT)
            addSuperinterfaces(nodeT)
            for (feature in concept.getOwnRoles()) {
                when (feature) {
                    is ProcessedProperty -> addConceptWrapperInterfaceProperty(feature)
                    is ProcessedChildLink -> addConceptWrapperInterfaceChildLink(feature)
                    is ProcessedReferenceLink -> addConceptWrapperInterfaceReferenceLink(feature)
                }
            }
            addCompanionObject()
        }
    }

    private fun TypeSpec.Builder.addCompanionObject() {
        val getInstanceInterfaceFun = FunSpec.builder(IConceptOfTypedNode<*>::getInstanceInterface.name).runBuild {
            addModifiers(KModifier.OVERRIDE)
            returns(KClass::class.asTypeName().parameterizedBy(concept.nodeWrapperInterfaceType()))
            addStatement("return %T::class", concept.nodeWrapperInterfaceType())
        }

        val untypedFun = FunSpec.builder(ITypedConcept::untyped.name).runBuild {
            returns(IConcept::class)
            addModifiers(KModifier.OVERRIDE)
            addStatement("return %T", concept.conceptObjectType())
        }

        val companionObj = TypeSpec.companionObjectBuilder().runBuild {
            addSuperinterface(concept.conceptWrapperInterfaceType())
            val t = if (concept.abstract) IConceptOfTypedNode::class else INonAbstractConcept::class
            addSuperinterface(t.asTypeName().parameterizedBy(concept.nodeWrapperInterfaceType()))
            addFunction(getInstanceInterfaceFun)
            addFunction(untypedFun)
            addConceptMetaPropertiesIfNecessary()
        }
        addType(companionObj)
    }

    private fun TypeSpec.Builder.addConceptMetaPropertiesIfNecessary() {
        if (conceptPropertiesInterfaceName == null) return

        concept.metaProperties.forEach { (key, value) ->
            val propertySpec = PropertySpec.builder(key, String::class.asTypeName()).runBuild {
                addModifiers(KModifier.OVERRIDE)
                initializer("%S", value)
            }

            addProperty(propertySpec)
        }
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceReferenceLink(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(referenceLink.generatedName, referenceLink.generatedReferenceLinkType()).runBuild {
            getter(FunSpec.getterBuilder().addCode(referenceLink.returnKotlinRef()).build())
            addDeprecationIfNecessary(referenceLink)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceChildLink(childLink: ProcessedChildLink) {
        val propertySpec = PropertySpec.builder(childLink.generatedName, childLink.generatedChildLinkType()).runBuild {
            getter(FunSpec.getterBuilder().addCode(childLink.returnKotlinRef()).build())
            addDeprecationIfNecessary(childLink)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = property.generatedName,
            type = GeneratedProperty::class.asClassName()
                .parameterizedBy(property.asKotlinType(alwaysUseNonNullableProperties)),
        ).runBuild {
            val getterSpec = FunSpec.getterBuilder().runBuild {
                addCode(property.returnKotlinRef())
            }
            getter(getterSpec)
            addDeprecationIfNecessary(property)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addSuperinterfaces(nodeT: TypeVariableName) {
        addSuperinterface(IConceptOfTypedNode::class.asTypeName().parameterizedBy(nodeT))
        for (extended in concept.getDirectSuperConcepts()) {
            addSuperinterface(extended.conceptWrapperInterfaceClass().parameterizedBy(nodeT))
        }

        if (conceptPropertiesInterfaceName != null && concept.extends.isEmpty()) {
            val pckgName = conceptPropertiesInterfaceName.substringBeforeLast(".")
            val interfaceName = conceptPropertiesInterfaceName.substringAfterLast(".")
            addSuperinterface(ClassName(pckgName, interfaceName))
        }
    }
}
