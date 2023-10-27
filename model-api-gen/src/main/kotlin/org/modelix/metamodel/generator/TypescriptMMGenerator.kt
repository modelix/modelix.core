package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.ClassName
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class TypescriptMMGenerator(
    val outputDir: Path,
    val nameConfig: NameConfig = NameConfig(),
    val runtimeNpmPackage: String,
) {

    private val fileHeaderText = """
//    ___                          _           _ _                              _      _ _
//   / _ \___ _ __   ___ _ __ __ _| |_ ___  __| | |__  _   _    /\/\   ___   __| | ___| (_)_  __
//  / /_\/ _ \ '_ \ / _ \ '__/ _` | __/ _ \/ _` | '_ \| | | |  /    \ / _ \ / _` |/ _ \ | \ \/ /
// / /_\\  __/ | | |  __/ | | (_| | ||  __/ (_| | |_) | |_| | / /\/\ \ (_) | (_| |  __/ | |>  <
// \____/\___|_| |_|\___|_|  \__,_|\__\___|\__,_|_.__/ \__, | \/    \/\___/ \__,_|\___|_|_/_/\_\
//                                                     |___/
            """.trim()

    fun generate(languages: IProcessedLanguageSet) {
        generate(languages as ProcessedLanguageSet)
    }

    internal fun generate(languages: ProcessedLanguageSet) {
        Files.createDirectories(outputDir)
        for (language in languages.getLanguages()) {
            // TODO delete old files from previous generation
            outputDir
                .resolve(language.generatedClassName().simpleName + ".ts")
                .fixFormatAndWriteText(generateLanguage(language))

            generateRegistry(languages)
        }
    }

    private fun fixFormat(input: CharSequence): CharSequence {
        val result = StringBuffer(input.length)
        var indentLevel = 0
        for (line in input.lineSequence()) {
            val trimmed = line.trimStart()
            if (trimmed.isEmpty()) continue
            repeat(indentLevel - (if (trimmed.startsWith("}")) 1 else 0)) {
                result.append("  ")
            }
            result.appendLine(trimmed)
            indentLevel += line.count { it == '{' } - line.count { it == '}' }
        }
        return result
    }

    private fun Path.fixFormatAndWriteText(text: String) = writeText(fixFormat(text))

    private fun generateRegistry(languages: ProcessedLanguageSet) {
        outputDir.resolve("index.ts").fixFormatAndWriteText(
            """$fileHeaderText

            """,
        )
    }

    private fun generateLanguage(language: ProcessedLanguage): String {
        return """$fileHeaderText
            import {
                ${
            listOf(
                language.name.substringBefore("."),
                "org",
                "INodeJS",
                "IConceptJS",
            ).distinct().sorted().joinToString(",\n")
        }
            } from "$runtimeNpmPackage";
            import ITypedNode = org.modelix.metamodel.ITypedNode
            import ITypedConcept = org.modelix.metamodel.ITypedConcept

            ${language.getConcepts().joinToString("\n") { generateConcept(it) }}
        """
    }

    private fun generateConcept(concept: ProcessedConcept): String {
        return """
            export function isOfConcept_${concept.name}(node: ITypedNode): node is ${concept.language.name}.${concept.nodeWrapperInterfaceName()} {
                return ${concept.language.name}._isOfConcept_${concept.name}(node);
            }
        """
    }

    private fun ProcessedConcept.nodeWrapperInterfaceName() = nameConfig.typedNode(this.name)

    private fun ProcessedLanguage.generatedClassName() = ClassName(name, nameConfig.languageClass(name))
}
