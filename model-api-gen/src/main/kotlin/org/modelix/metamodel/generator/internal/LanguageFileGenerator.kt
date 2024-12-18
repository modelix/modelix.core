package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.GeneratedLanguage
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.ProcessedLanguage
import org.modelix.metamodel.generator.runBuild
import org.modelix.metamodel.generator.toListLiteralCodeBlock
import org.modelix.model.api.IConcept
import java.nio.file.Path

internal class LanguageFileGenerator(
    private val language: ProcessedLanguage,
    override val outputDir: Path,
    override val nameConfig: NameConfig,
) : NameConfigBasedGenerator(nameConfig), FileGenerator {

    override fun generateFileSpec(): FileSpec {
        return FileSpec.builder(
            packageName = language.generatedClassName().packageName,
            fileName = language.generatedClassName().simpleName,
        ).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addType(generateLanguage())
        }
    }

    private fun generateLanguage(): TypeSpec {
        val getConceptsFun = FunSpec.builder("getConcepts").runBuild {
            returns(List::class.asClassName().parameterizedBy(IConcept::class.asTypeName()))
            addModifiers(KModifier.OVERRIDE)
            addCode(language.getConcepts().map { it.conceptObjectType() }.toListLiteralCodeBlock())
        }

        return TypeSpec.objectBuilder(language.generatedClassName()).runBuild {
            addFunction(getConceptsFun)
            superclass(GeneratedLanguage::class)
            addSuperclassConstructorParameter("\"${language.name}\"")

            for (concept in language.getConcepts()) {
                val property = PropertySpec.builder(concept.name, concept.conceptWrapperInterfaceType()).runBuild {
                    initializer("%T", concept.conceptWrapperInterfaceClass())
                }
                addProperty(property)
            }
        }
    }
}
