package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.ProcessedLanguageSet
import org.modelix.metamodel.generator.runBuild
import java.nio.file.Path

internal class MetaPropertiesInterfaceGenerator(
    private val languages: ProcessedLanguageSet,
    override val outputDir: Path,
    private val fqInterfaceName: String,
) : FileGenerator {

    override fun generateFileSpec(): FileSpec {
        require(fqInterfaceName.contains(".")) { "The name of the concept properties interface does not contain a dot. Use a fully qualified name." }
        val interfaceName = ClassName(fqInterfaceName.substringBeforeLast("."), fqInterfaceName.substringAfterLast("."))

        return FileSpec.builder(interfaceName.packageName, interfaceName.simpleName).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addMetaPropertiesInterface(interfaceName)
        }
    }

    private fun FileSpec.Builder.addMetaPropertiesInterface(interfaceName: ClassName) {
        val nullGetter = FunSpec.getterBuilder().runBuild {
            addCode("return null")
        }

        val metaPropertiesInterface = TypeSpec.interfaceBuilder(interfaceName).runBuild {
            for (metaProperty in languages.getConceptMetaProperties()) {
                addMetaProperty(metaProperty, nullGetter)
            }
        }

        addType(metaPropertiesInterface)
    }

    private fun TypeSpec.Builder.addMetaProperty(metaPropertyName: String, nullGetter: FunSpec) {
        val property = PropertySpec.builder(
            name = metaPropertyName,
            type = String::class.asTypeName().copy(nullable = true),
        ).runBuild {
            getter(nullGetter)
        }
        addProperty(property)
    }
}
