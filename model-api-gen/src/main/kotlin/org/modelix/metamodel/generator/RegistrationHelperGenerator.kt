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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.modelix.metamodel.GeneratedLanguage
import java.nio.file.Path

internal class RegistrationHelperGenerator(
    private val classFqName: String,
    private val languages: ProcessedLanguageSet,
    private val outputDir: Path,
    override val nameConfig: NameConfig,
) : NameConfigBasedGenerator(nameConfig) {

    fun generateFile() {
        generateSpec().writeTo(outputDir)
    }

    private fun generateSpec(): FileSpec {
        require(classFqName.contains(".")) { "The name of the registrationHelper does not contain a dot. Use a fully qualified name." }
        val typeName = ClassName(classFqName.substringBeforeLast("."), classFqName.substringAfterLast("."))

        val languagesProperty = PropertySpec.builder(
            name = "languages",
            type = List::class.parameterizedBy(GeneratedLanguage::class),
        ).runBuild {
            initializer(
                buildString {
                    append("listOf(")
                    append(
                        languages.getLanguages()
                            .map { it.generatedClassName() }
                            .joinToString(", ") { it.canonicalName },
                    )
                    append(")")
                },
            )
        }

        val registerAllFun = FunSpec.builder("registerAll").runBuild {
            addStatement("""languages.forEach { it.register() }""")
        }

        val registrationHelperClass = TypeSpec.objectBuilder(typeName).runBuild {
            addProperty(languagesProperty)
            addFunction(registerAllFun)
        }

        return FileSpec.builder(typeName.packageName, typeName.simpleName).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addType(registrationHelperClass)
        }
    }
}
