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
