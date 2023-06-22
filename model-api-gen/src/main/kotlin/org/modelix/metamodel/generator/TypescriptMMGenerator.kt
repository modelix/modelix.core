package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.ClassName
import org.modelix.model.data.LanguageData
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class TypescriptMMGenerator(val outputDir: Path, val nameConfig: NameConfig = NameConfig()) {

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

    fun generate(languages: IProcessedLanguageSet) {
        generate(languages as ProcessedLanguageSet)
    }

    internal fun generate(languages: ProcessedLanguageSet) {
        Files.createDirectories(outputDir)
        for (language in languages.getLanguages()) {
            // TODO delete old files from previous generation
            outputDir
                .resolve(language.generatedClassName().simpleName + ".ts")
                .writeText(generateLanguage(language))

            generateRegistry(languages)
        }
    }

    private fun generateRegistry(languages: ProcessedLanguageSet) {
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

    private fun generateLanguage(language: ProcessedLanguage): String {
        val conceptNamesList = language.getConcepts()
            .joinToString(", ") { it.conceptWrapperInterfaceName() }

        return """
            import {
                ChildListAccessor,
                GeneratedConcept, 
                GeneratedLanguage,
                IConceptJS,
                INodeJS,
                ITypedNode, 
                SingleChildAccessor,
                TypedNode,
                LanguageRegistry
            } from "@modelix/ts-model-api";
            
            ${language.languageDependencies().joinToString("\n") {
                """import * as ${it.simpleClassName()} from "./${it.simpleClassName()}";"""
            }}
            
            export class ${language.simpleClassName()} extends GeneratedLanguage {
                public static INSTANCE: ${language.simpleClassName()} = new ${language.simpleClassName()}();
                constructor() {
                    super("${language.name}")
                    
                    ${language.getConcepts().joinToString("\n") { concept -> """
                        this.nodeWrappers.set("${concept.uid}", (node: INodeJS) => new ${concept.nodeWrapperImplName()}(node))
                    """.trimIndent() }}
                }
                public getConcepts() {
                    return [$conceptNamesList]
                }
            }
            
            ${language.getConcepts().joinToString("\n") { generateConcept(it) }.replaceIndent("            ")}
        """.trimIndent()
    }

    private fun generateConcept(concept: ProcessedConcept): String {
        val featuresImpl = concept.getAllSuperConceptsAndSelf().flatMap { it.getOwnRoles() }.joinToString("\n") { feature ->
            when (feature) {
                is ProcessedProperty -> {
                    val rawValueName = feature.rawValueName()
                    val rawPropertyText = """
                        public set $rawValueName(value: string | undefined) {
                            this._node.setPropertyValue("${feature.originalName}", value)
                        }
                        public get $rawValueName(): string | undefined {
                            return this._node.getPropertyValue("${feature.originalName}")
                        }
                    """.trimIndent()
                    val typedPropertyText = if (feature.type is PrimitivePropertyType) {
                        when((feature.type as PrimitivePropertyType).primitive) {
                            Primitive.INT -> {
                                """
                                public set ${feature.generatedName}(value: number) {
                                    this.${rawValueName} = value.toString();
                                }
                                public get ${feature.generatedName}(): number {
                                    let str = this.${rawValueName};
                                    return str ? parseInt(str) : 0;
                                }
                                
                            """.trimIndent()
                            }
                            Primitive.BOOLEAN -> {
                                """
                                public set ${feature.generatedName}(value: boolean) {
                                    this.${rawValueName} = value ? "true" : "false";
                                }
                                public get ${feature.generatedName}(): boolean {
                                    return this.${rawValueName} === "true";
                                }
                                
                            """.trimIndent()
                            }
                            Primitive.STRING -> """
                                public set ${feature.generatedName}(value: string) {
                                    this.${rawValueName} = value;
                                }
                                public get ${feature.generatedName}(): string {
                                    return this.${rawValueName} ?? "";
                                }
                                
                            """.trimIndent()
                        }
                    } else ""
                    """
                        $rawPropertyText
                        $typedPropertyText
                    """.trimIndent()
                }
                is ProcessedReferenceLink -> {
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    val entityType = "$languagePrefix${typeRef.nodeWrapperInterfaceName()}"
                    """
                    public set ${feature.generatedName}(value: $entityType | undefined) {
                        this._node.setReferenceTargetNode("${feature.originalName}", value?.unwrap());
                    }
                    public get ${feature.generatedName}(): $entityType | undefined {
                        let target = this._node.getReferenceTargetNode("${feature.originalName}");
                        return target ? LanguageRegistry.INSTANCE.wrapNode(target) as $entityType : undefined;
                    }
                """.trimIndent()
                }
                is ProcessedChildLink -> {
                    val accessorClassName = if (feature.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    """
                        public ${feature.generatedName}: $accessorClassName<$languagePrefix${typeRef.nodeWrapperInterfaceName()}> = new $accessorClassName(this._node, "${feature.originalName}")
                    """.trimIndent()
                }
                else -> ""
            }
        }
        val features = concept.getOwnRoles().joinToString("\n") { feature ->
            when (feature) {
                is ProcessedProperty -> {
                    val rawPropertyText = """
                        ${feature.rawValueName()}: string | undefined
                    """.trimIndent()
                    val typedPropertyText = if (feature.type is PrimitivePropertyType) {
                        when ((feature.type as PrimitivePropertyType).primitive) {
                            Primitive.BOOLEAN -> {
                                """
                                ${feature.generatedName}: boolean
                                
                                """.trimIndent()
                            }
                            Primitive.INT -> {
                                """
                                ${feature.generatedName}: number
                                
                                """.trimIndent()
                            }
                            Primitive.STRING -> {
                                """
                                ${feature.generatedName}: string
                                
                                """.trimIndent()
                            }
                        }
                    } else ""
                    """
                        $rawPropertyText
                        $typedPropertyText
                    """.trimIndent()
                }
                is ProcessedReferenceLink -> {
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    val entityType = "$languagePrefix${typeRef.nodeWrapperInterfaceName()}"
                        """
                        set ${feature.generatedName}(value: $entityType | undefined);
                        get ${feature.generatedName}(): $entityType | undefined;
                    """.trimIndent()
                }
                is ProcessedChildLink -> {
                    val accessorClassName = if (feature.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    """
                        ${feature.generatedName}: $accessorClassName<${feature.type.resolved.tsInterfaceRef(concept.language)}>
                    """.trimIndent()
                }
                else -> ""
            }
        }
        val interfaceList = concept.getDirectSuperConcepts().joinToString(", ") { it.tsInterfaceRef(concept.language) }.ifEmpty { "ITypedNode" }
        // TODO extend first super concept do reduce the number of generated members
        return """
            
            export class ${concept.conceptWrapperImplName()} extends GeneratedConcept {
              constructor(uid: string) {
                super(uid);
              }
              getDirectSuperConcepts(): Array<IConceptJS> {
                return [${concept.getDirectSuperConcepts().joinToString(",") { it.languagePrefix(concept.language) + it.conceptWrapperInterfaceName() }}];
              }
            }
            export const ${concept.conceptWrapperInterfaceName()} = new ${concept.conceptWrapperImplName()}("${concept.uid}")
            
            export interface ${concept.nodeWrapperInterfaceName()} extends $interfaceList {
                ${features}
            }
            
            export function isOfConcept_${concept.name}(node: ITypedNode): node is ${concept.nodeWrapperInterfaceName()} {
                return '${concept.markerPropertyName()}' in node.constructor;
            }
            
            export class ${concept.nodeWrapperImplName()} extends TypedNode implements ${concept.nodeWrapperInterfaceName()} {
                ${concept.getAllSuperConceptsAndSelf().joinToString("\n") {
                    """public static readonly ${it.markerPropertyName()}: boolean = true"""
                }}
                ${featuresImpl.replaceIndent("                ")}
            }
            
        """.trimIndent()
    }

    private fun ProcessedConcept.nodeWrapperInterfaceName() =
        nameConfig.typedNode(this.name)

    private fun ProcessedConcept.conceptWrapperImplName() =
        nameConfig.typedConceptImpl(this.name)

    private fun ProcessedConcept.nodeWrapperImplName() =
        nameConfig.typedNodeImpl(this.name)

    private fun ProcessedConcept.conceptWrapperInterfaceName() =
        nameConfig.typedConcept(this.name)

    private fun ProcessedLanguage.generatedClassName() =
        ClassName(name, nameConfig.languageClass(name))

    private fun ProcessedLanguage.simpleClassName() =
        this.generatedClassName().simpleName

    private fun ProcessedConcept.markerPropertyName() = "_is_" + this.fqName().replace(".", "_")
    //private fun ProcessedConcept.tsClassName() = nameConfig.languageClassName(this.language.name) + "." + this.name
    private fun ProcessedConcept.tsInterfaceRef(contextLanguage: ProcessedLanguage) = languagePrefix(contextLanguage) + nodeWrapperInterfaceName()
    private fun ProcessedConcept.languagePrefix(contextLanguage: ProcessedLanguage): String {
        return if (this.language == contextLanguage) {
            ""
        } else {
            nameConfig.languageClass(this.language.name) + "."
        }
    }
}

internal fun ProcessedLanguage.languageDependencies(): List<ProcessedLanguage> {
    val languageNames = this.getConcepts()
        .flatMap { it.getAllSuperConceptsAndSelf().flatMap { it.getOwnRoles() } }
        .mapNotNull {
            when (it) {
                is ProcessedLink -> it.type.resolved
                else -> null
            }
        }
        .plus(this.getConcepts().flatMap { it.getDirectSuperConcepts() })
        .map { it.language.name }
        .toSet()
    return languageSet.getLanguages().filter { languageNames.contains(it.name) }.minus(this)
}

private fun ProcessedProperty.rawValueName() = "raw_$generatedName"
