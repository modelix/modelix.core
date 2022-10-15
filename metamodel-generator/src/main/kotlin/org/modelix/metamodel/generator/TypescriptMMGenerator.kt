package org.modelix.metamodel.generator

import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

class TypescriptMMGenerator(val outputDir: Path) {
    private val languagesMap = HashMap<String, LanguageData>()
    private val conceptsMap = HashMap<String, ConceptInLanguage>()

    private fun LanguageData.packageDir(): Path {
        val packageName = name
        var packageDir = outputDir
        if (packageName.isNotEmpty()) {
            for (packageComponent in packageName.split('.').dropLastWhile { it.isEmpty() }) {
                packageDir = packageDir.resolve(packageComponent)
            }
        }
        return packageDir
    }

    fun generate(languages: List<LanguageData>, filter: ConceptsFilter? = null) {
        for (language in languages.filter { filter?.isLanguageIncluded(it.name) ?: true }) {
            languagesMap[language.name] = language
            for (concept in language.getConceptsInLanguage().filter { filter?.isConceptIncluded(it.fqName) ?: true }) {
                conceptsMap[concept.fqName] = concept
            }
        }

        for (language in languages.filter { filter?.isLanguageIncluded(it.name) ?: true }) {
            language.packageDir().toFile().listFiles()?.filter { it.isFile }?.forEach { it.delete() }

            outputDir
                .resolve(language.generatedClassName().simpleName + ".ts")
                .writeText(generateLanguage(language, filter))

            for (concept in language.getConceptsInLanguage().filter { filter?.isConceptIncluded(it.getConceptFqName()) ?: true }) {
                //generateConceptFile(concept)
            }
        }
    }

    private fun generateLanguage(language: LanguageData, filter: ConceptsFilter?): String {
        val conceptNamesList = language.concepts
            .filter { filter?.isConceptIncluded(ConceptInLanguage(it, language).getConceptFqName()) ?: true }
            .joinToString(", ") { "this." + it.name }

        val conceptFields = language.concepts
            .filter { filter?.isConceptIncluded(ConceptInLanguage(it, language).getConceptFqName()) ?: true }
            .joinToString("\n") { """public ${it.name}: ${it.conceptObjectName()} = ${it.conceptObjectName()}""" }

        return """
            export class ${language.generatedClassName().simpleName} extends GeneratedLanguage {
                constructor() {
                    super("${language.name}")
                }
                public getConcepts() {
                    return [$conceptNamesList]
                }
                ${conceptFields.replaceIndent("                ")}
            }
        """.trimIndent()

    }
}