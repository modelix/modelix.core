package org.modelix.metamodel.generator

import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

class TypescriptMMGenerator(val outputDir: Path) {

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

    fun generate(languages: LanguageSet) {
        for (language in languages.getLanguages()) {
            // TODO delete old files from previous generation
            outputDir
                .resolve(language.language.generatedClassName().simpleName + ".ts")
                .writeText(generateLanguage(language))

            generateRegistry(languages)
        }
    }

    private fun generateRegistry(languages: LanguageSet) {
        outputDir.resolve("index.ts").writeText("""
            import { LanguageRegistry } from "ts-model-api";
            ${languages.getLanguages().joinToString("\n") { """
                import { ${it.simpleClassName()} } from "./${it.simpleClassName()}";
            """.trimIndent() }}
            export function registerLanguages() {
                ${languages.getLanguages().joinToString("\n") { """
                    LanguageRegistry.INSTANCE.register(${it.simpleClassName()}.INSTANCE);
                """.trimIndent() }}
            }
        """.trimIndent())
    }

    private fun generateLanguage(language: LanguageSet.LanguageInSet): String {
        val conceptNamesList = language.getConceptsInLanguage()
            .joinToString(", ") { "this." + it.simpleName }

        val conceptFields = language.getConceptsInLanguage()
            .joinToString("\n") { """public ${it.simpleName}: ${it.concept.conceptObjectName()} = ${it.concept.conceptObjectName()}""" }

        return """
            import {
              ChildListAccessor,
              SingleChildAccessor,
              GeneratedLanguage,
              TypedNode
            } from "ts-model-api";
            
            export namespace ${language.simpleClassName()} {
            
            export class ${language.simpleClassName()} extends GeneratedLanguage {
                public static INSTANCE: ${language.simpleClassName()} = new ${language.simpleClassName()}();
                constructor() {
                    super("${language.name}")
                }
                /*
                public getConcepts() {
                    return [$conceptNamesList]
                }
                ${conceptFields.replaceIndent("                ")}
                */
            }
            
            ${language.getConceptsInLanguage().joinToString("\n") { generateConcept(it) }.replaceIndent("            ")}
            }
        """.trimIndent()
    }

    private fun generateConcept(concept: LanguageSet.ConceptInLanguage): String {
        val features = concept.allFeatures().joinToString("\n") { feature ->
            when (val data = feature.data) {
                is PropertyData -> """
                    public set ${data.name}(value: string | undefined) {
                        this.node.setPropertyValue("${data.name}", value)
                    }
                    public get ${data.name}(): string | undefined {
                        return this.node.getPropertyValue("${data.name}")
                    }
                """.trimIndent()
                is ReferenceLinkData -> """
                    
                """.trimIndent()
                is ChildLinkData -> {
                    val accessorClassName = if (data.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    """
                        public ${data.name}: $accessorClassName<TypedNode> = new $accessorClassName(this.node, "${data.name}")
                    """.trimIndent()
                }
                else -> ""
            }
        }
        return """
            
            export class ${concept.concept.name} extends TypedNode {
                ${features.replaceIndent("                ")}
            }
        """.trimIndent()
    }
}