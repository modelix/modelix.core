package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.ClassName
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.data.LanguageData
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class TypescriptMMGenerator(val outputDir: Path, val nameConfig: NameConfig = NameConfig(), private val includeTsBarrels: Boolean = false) {

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
                .fixFormatAndWriteText(generateLanguage(language))

            generateIndexTs(languages)
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

    private fun generateIndexTs(languages: ProcessedLanguageSet) {
        outputDir.resolve("index.ts").fixFormatAndWriteText(
            """
            import { LanguageRegistry } from "@modelix/ts-model-api";
            ${languages.getLanguages().joinToString("\n") { """
                import { ${it.simpleClassName()} } from "./${it.simpleClassName()}";
            """
            }}
            export function registerLanguages() {
                ${languages.getLanguages().joinToString("\n") { """
                    LanguageRegistry.INSTANCE.register(${it.simpleClassName()}.INSTANCE);
            """
            }}
            }
            ${if (!includeTsBarrels) {
                ""
            } else {
                languages.getLanguages().joinToString("\n") {
                    """export * from "./${it.simpleClassName()}";"""
                }
            }}
            """,
        )
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
                toRoleJS,
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

                    ${language.getConcepts().joinToString("\n") { concept ->
            """
                        this.nodeWrappers.set("${concept.uid}", (node: INodeJS) => new ${concept.nodeWrapperImplName()}(node))
            """
        }}
                }
                public getConcepts() {
                    return [$conceptNamesList]
                }
            }

            ${language.getConcepts().joinToString("\n") { generateConcept(it) }.replaceIndent("            ")}
        """
    }

    private fun generateConcept(concept: ProcessedConcept): String {
        val featuresImpl = concept.getAllSuperConceptsAndSelf().flatMap { it.getOwnRoles() }.joinToString("\n") { feature ->
            when (feature) {
                is ProcessedProperty -> {
                    val rawValueName = feature.rawValueName()
                    val stringForLegacyApi =
                        IPropertyReference.fromIdAndName(feature.uid, feature.originalName).stringForLegacyApi()
                    val rawPropertyText = """
                        public set $rawValueName(value: string | undefined) {
                            this._node.setPropertyValue(toRoleJS("$stringForLegacyApi"), value)
                        }
                        public get $rawValueName(): string | undefined {
                            return this._node.getPropertyValue(toRoleJS("$stringForLegacyApi"))
                        }
                    """
                    val typedPropertyText = if (feature.type is PrimitivePropertyType) {
                        when ((feature.type as PrimitivePropertyType).primitive) {
                            Primitive.INT -> {
                                """
                                public set ${feature.generatedName}(value: number) {
                                    this.$rawValueName = value.toString();
                                }
                                public get ${feature.generatedName}(): number {
                                    let str = this.$rawValueName;
                                    return str ? parseInt(str) : 0;
                                }

                                """
                            }
                            Primitive.BOOLEAN -> {
                                """
                                public set ${feature.generatedName}(value: boolean) {
                                    this.$rawValueName = value ? "true" : "false";
                                }
                                public get ${feature.generatedName}(): boolean {
                                    return this.$rawValueName === "true";
                                }

                                """
                            }
                            Primitive.STRING ->
                                """
                                public set ${feature.generatedName}(value: string) {
                                    this.$rawValueName = value;
                                }
                                public get ${feature.generatedName}(): string {
                                    return this.$rawValueName ?? "";
                                }

                            """
                        }
                    } else {
                        ""
                    }
                    """
                        $rawPropertyText
                        $typedPropertyText
                    """
                }
                is ProcessedReferenceLink -> {
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    val entityType = "$languagePrefix${typeRef.nodeWrapperInterfaceName()}"
                    val stringForLegacyApi =
                        IReferenceLinkReference.fromIdAndName(feature.uid, feature.originalName).stringForLegacyApi()
                    """
                    public set ${feature.generatedName}(value: $entityType | undefined) {
                        this._node.setReferenceTargetNode(toRoleJS("$stringForLegacyApi"), value?.unwrap());
                    }
                    public get ${feature.generatedName}(): $entityType | undefined {
                        let target = this._node.getReferenceTargetNode(toRoleJS("$stringForLegacyApi"));
                        return target ? LanguageRegistry.INSTANCE.wrapNode(target) as $entityType : undefined;
                    }
                    """
                }
                is ProcessedChildLink -> {
                    val accessorClassName = if (feature.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    val stringForLegacyApi =
                        IChildLinkReference.fromIdAndName(feature.uid, feature.originalName).stringForLegacyApi()
                    """
                        public ${feature.generatedName}: $accessorClassName<$languagePrefix${typeRef.nodeWrapperInterfaceName()}> = new $accessorClassName(this._node, toRoleJS("$stringForLegacyApi"))
                    """
                }
                else -> ""
            }
        }
        val features = concept.getOwnRoles().joinToString("\n") { feature ->
            when (feature) {
                is ProcessedProperty -> {
                    val rawPropertyText = """
                        ${feature.rawValueName()}: string | undefined
                    """
                    val typedPropertyText = if (feature.type is PrimitivePropertyType) {
                        when ((feature.type as PrimitivePropertyType).primitive) {
                            Primitive.BOOLEAN -> {
                                """
                                ${feature.generatedName}: boolean

                                """
                            }
                            Primitive.INT -> {
                                """
                                ${feature.generatedName}: number

                                """
                            }
                            Primitive.STRING -> {
                                """
                                ${feature.generatedName}: string

                                """
                            }
                        }
                    } else {
                        ""
                    }
                    """
                        $rawPropertyText
                        $typedPropertyText
                    """
                }
                is ProcessedReferenceLink -> {
                    val typeRef = feature.type.resolved
                    val languagePrefix = typeRef.languagePrefix(concept.language)
                    val entityType = "$languagePrefix${typeRef.nodeWrapperInterfaceName()}"
                    """
                        set ${feature.generatedName}(value: $entityType | undefined);
                        get ${feature.generatedName}(): $entityType | undefined;
                    """
                }
                is ProcessedChildLink -> {
                    val accessorClassName = if (feature.multiple) "ChildListAccessor" else "SingleChildAccessor"
                    """
                        ${feature.generatedName}: $accessorClassName<${feature.type.resolved.tsInterfaceRef(concept.language)}>
                    """
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
                $features
            }

            export function isOfConcept_${concept.name}(node: ITypedNode | null | undefined): node is ${concept.nodeWrapperInterfaceName()} {
                return node?.constructor !== undefined && '${concept.markerPropertyName()}' in node.constructor;
            }

            export class ${concept.nodeWrapperImplName()} extends TypedNode implements ${concept.nodeWrapperInterfaceName()} {
                ${concept.getAllSuperConceptsAndSelf().joinToString("\n") {
            """public static readonly ${it.markerPropertyName()}: boolean = true"""
        }}
                ${featuresImpl.replaceIndent("                ")}
            }

        """
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

    // private fun ProcessedConcept.tsClassName() = nameConfig.languageClassName(this.language.name) + "." + this.name
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
