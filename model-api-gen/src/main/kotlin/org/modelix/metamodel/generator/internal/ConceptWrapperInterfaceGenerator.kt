package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.IConceptOfTypedNode
import org.modelix.metamodel.INonAbstractConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedChildLink
import org.modelix.metamodel.generator.ProcessedConcept
import org.modelix.metamodel.generator.ProcessedProperty
import org.modelix.metamodel.generator.ProcessedReferenceLink
import org.modelix.metamodel.generator.addDeprecationIfNecessary
import org.modelix.metamodel.generator.runBuild
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

        for ((key, value) in concept.metaProperties) {
            val propertySpec = PropertySpec.builder(key, String::class.asTypeName()).runBuild {
                addModifiers(KModifier.OVERRIDE)
                initializer("%S", value)
            }

            addProperty(propertySpec)
        }
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceReferenceLink(referenceLink: ProcessedReferenceLink) {
        val propertySpec = PropertySpec.builder(referenceLink.generatedName, referenceLink.typedLinkType()).runBuild {
            getter(FunSpec.getterBuilder().addCode(referenceLink.returnKotlinRef()).build())
            addDeprecationIfNecessary(referenceLink)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceChildLink(childLink: ProcessedChildLink) {
        val propertySpec = PropertySpec.builder(childLink.generatedName, childLink.typedLinkType()).runBuild {
            getter(FunSpec.getterBuilder().addCode(childLink.returnKotlinRef()).build())
            addDeprecationIfNecessary(childLink)
        }

        addProperty(propertySpec)
    }

    private fun TypeSpec.Builder.addConceptWrapperInterfaceProperty(property: ProcessedProperty) {
        val propertySpec = PropertySpec.builder(
            name = property.generatedName,
            type = property.typedPropertyType(alwaysUseNonNullableProperties),
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
