package org.modelix.metamodel.generator

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
            import { LanguageRegistry } from "@modelix/ts-model-api";
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
            .joinToString(", ") { it.concept.conceptWrapperInterfaceName() }

        return """
            import {
                ChildListAccessor,
                GeneratedConcept, 
                GeneratedLanguage,
                IConceptJS,
                INodeJS,
                ITypedNode, 
                SingleChildAccessor,
                TypedNode
            } from "@modelix/ts-model-api";
            
            ${language.languageDependencies().joinToString("\n") {
                """import * as ${it.simpleClassName()} from "./${it.simpleClassName()}";"""
            }}
            
            export class ${language.simpleClassName()} extends GeneratedLanguage {
                public static INSTANCE: ${language.simpleClassName()} = new ${language.simpleClassName()}();
                constructor() {
                    super("${language.name}")
                    
                    ${language.getConceptsInLanguage().joinToString("\n") { concept -> """
                        this.nodeWrappers.set("${concept.uid}", (node: INodeJS) => new ${concept.concept.nodeWrapperImplName()}(node))
                    """.trimIndent() }}
                }
                public getConcepts() {
                    return [$conceptNamesList]
                }
            }
            
            ${language.getConceptsInLanguage().joinToString("\n") { generateConcept(it) }.replaceIndent("            ")}
        """.trimIndent()
    }

    private fun generateConcept(concept: LanguageSet.ConceptInLanguage): String {
        val featuresImpl = concept.allFeatures().joinToString("\n") { feature ->
            when (val data = feature.data) {
                is PropertyData -> {
                    val rawValueName = feature.rawValueName()
                    when (data.type) {
                        PropertyType.INT -> {
                            """
                                public set ${feature.validName}(value: number) {
                                    this.${rawValueName} = value.toString();
                                }
                                public get ${feature.validName}(): number {
                                    let str = this.${rawValueName};
                                    return str ? parseInt(str) : 0;
                                }
                                
                            """.trimIndent()
                        }
                        PropertyType.BOOLEAN -> {
                            """
                                public set ${feature.validName}(value: boolean) {
                                    this.${rawValueName} = value ? "true" : "false";
                                }
                                public get ${feature.validName}(): boolean {
                                    return this.${rawValueName} === "true";
                                }
                                
                            """.trimIndent()
                        }
                        else -> ""
                    } + """
                        public set $rawValueName(value: string | undefined) {
                            this.node.setPropertyValue("${data.name}", value)
                        }
                        public get $rawValueName(): string | undefined {
                            return this.node.getPropertyValue("${data.name}")
                        }
                    """.trimIndent()
                }
                is ReferenceLinkData -> """
                    
                """.trimIndent()
                is ChildLinkData -> {
                    val accessorClassName = if (data.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    val typeRef = data.type.parseConceptRef(concept.language)
                    val languagePrefix = typeRef.languagePrefix(concept.language.name)
                    """
                        public ${feature.validName}: $accessorClassName<$languagePrefix${typeRef.conceptName.nodeWrapperInterfaceName()}> = new $accessorClassName(this.node, "${data.name}")
                    """.trimIndent()
                }
                else -> ""
            }
        }
        val features = concept.directFeatures().joinToString("\n") { feature ->
            when (val data = feature.data) {
                is PropertyData -> {
                    when (data.type) {
                        PropertyType.BOOLEAN -> {
                            """
                                ${feature.validName}: boolean
                                
                            """.trimIndent()
                        }
                        PropertyType.INT -> {
                            """
                                ${feature.validName}: number
                                
                            """.trimIndent()
                        }
                        else -> ""
                    } +
                    """
                        ${feature.rawValueName()}: string | undefined
                    """.trimIndent()
                }
                is ReferenceLinkData -> """
                    
                """.trimIndent()
                is ChildLinkData -> {
                    val accessorClassName = if (data.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    """
                        ${feature.validName}: $accessorClassName<${data.type.parseConceptRef(concept.language).tsInterfaceRef(concept.language.name)}>
                    """.trimIndent()
                }
                else -> ""
            }
        }
        val interfaceList = concept.directSuperConcepts().joinToString(", ") { it.ref().tsInterfaceRef(concept.language.name) }.ifEmpty { "ITypedNode" }
        // TODO extend first super concept do reduce the number of generated members
        return """
            
            export class ${concept.concept.conceptWrapperImplName()} extends GeneratedConcept {
              constructor(uid: string) {
                super(uid);
              }
              getDirectSuperConcepts(): Array<IConceptJS> {
                return [${concept.directSuperConcepts().joinToString(",") { it.ref().languagePrefix(concept.language.name) + it.concept.conceptWrapperInterfaceName() }}];
              }
            }
            export const ${concept.concept.conceptWrapperInterfaceName()} = new ${concept.concept.conceptWrapperImplName()}("${concept.uid}")
            
            export interface ${concept.concept.nodeWrapperInterfaceName()} extends $interfaceList {
                ${features}
            }
            
            export function isOfConcept_${concept.concept.name}(node: ITypedNode): node is ${concept.concept.nodeWrapperInterfaceName()} {
                return '${concept.ref().markerPropertyName()}' in node.constructor;
            }
            
            export class ${concept.concept.nodeWrapperImplName()} extends TypedNode implements ${concept.concept.nodeWrapperInterfaceName()} {
                ${concept.allSuperConceptsAndSelf().joinToString("\n") {
                    """public static readonly ${it.ref().markerPropertyName()}: boolean = true"""
                }}
                ${featuresImpl.replaceIndent("                ")}
            }
            
        """.trimIndent()
    }
}

private fun ConceptRef.markerPropertyName() = "_is_" + toString().replace(".", "_")
fun ConceptRef.tsClassName() = this.languageName.languageClassName() + "." + this.conceptName
fun ConceptRef.tsInterfaceRef(contextLanguage: String) = languagePrefix(contextLanguage) + this.conceptName.nodeWrapperInterfaceName()
fun ConceptRef.languagePrefix(contextLanguage: String): String {
    return if (this.languageName == contextLanguage) {
        ""
    } else {
        this.languageName.languageClassName() + "."
    }
}
fun LanguageSet.ConceptInLanguage.tsClassName() = ConceptRef(language.name, concept.name).tsClassName()
fun LanguageSet.LanguageInSet.languageDependencies(): List<LanguageSet.LanguageInSet> {
    val languageNames = this.getConceptsInLanguage()
        .flatMap { it.allFeatures() }
        .mapNotNull {
            when (val data = it.data) {
                is ChildLinkData -> data.type
                is ReferenceLinkData -> data.type
                else -> null
            }?.parseConceptRef(language)
        }
        .plus(this.getConceptsInLanguage().flatMap { it.directSuperConcepts() }.map { it.ref() })
        .map { it.languageName }
        .toSet()
    return getLanguageSet().getLanguages().filter { languageNames.contains(it.name) }.minus(this)
}

private fun FeatureInConcept.rawValueName() = when ((data as PropertyData).type) {
    PropertyType.STRING -> validName
    else -> "raw_" + validName
}