package org.modelix.metamodel.export

import jetbrains.mps.lang.core.behavior.IDeprecatable__BehaviorDescriptor
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SEnumOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SLinkOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPointerOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.lang.structure.behavior.AbstractConceptDeclaration__BehaviorDescriptor
import jetbrains.mps.lang.structure.behavior.LinkDeclaration__BehaviorDescriptor
import jetbrains.mps.smodel.JavaFriendlyBase64
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.LanguageAspect
import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.smodel.adapter.ids.MetaIdByDeclaration
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.metamodel.export.MPSModelExporter.Companion.exportNode
import org.modelix.model.data.AnnotationData
import org.modelix.model.data.ChildLinkData
import org.modelix.model.data.ConceptData
import org.modelix.model.data.EnumData
import org.modelix.model.data.EnumMemberData
import org.modelix.model.data.EnumPropertyType
import org.modelix.model.data.LanguageData
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import org.modelix.model.data.PropertyData
import org.modelix.model.data.ReferenceLinkData
import java.io.File
import java.nio.charset.StandardCharsets

class MPSMetaModelExporter(private val outputFolder: File) {
    private val processedLanguages: MutableSet<Language> = HashSet()
    private val producedData: MutableMap<Language, LanguageData> = HashMap()

    val numLanguages: Int
        get() = processedLanguages.size

    private fun exportLanguage(nodeInLanguage: SNode?) {
        if ((nodeInLanguage == null)) {
            return
        }
        val model = SNodeOperations.getModel(nodeInLanguage) ?: return
        val language = model.module as? Language ?: return
        exportLanguage(language)
    }

    fun exportLanguage(languageModule: Language) {
        if (processedLanguages.contains(languageModule)) {
            return
        }
        processedLanguages.add(languageModule)

        val structureModel = LanguageAspect.STRUCTURE[languageModule] ?: return
        val rootNodes = structureModel.rootNodes

        val concepts = SNodeOperations.ofConcept(rootNodes, CONCEPTS.AbstractConceptDeclaration).map { concept: SNode ->
            val properties = SLinkOperations.getChildren(concept, LINKS.propertyDeclaration)
                .map { it: SNode ->
                    val type = if (SLinkOperations.getPointer(it, LINKS.dataType) == SNodePointer("r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)", "1082983657062")) {
                        PrimitivePropertyType(Primitive.INT)
                    } else if (SLinkOperations.getPointer(it, LINKS.dataType) == SNodePointer("r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)", "1082983657063")) {
                        PrimitivePropertyType(Primitive.BOOLEAN)
                    } else if (SNodeOperations.isInstanceOf(SLinkOperations.getTarget(it, LINKS.dataType), CONCEPTS.EnumerationDeclaration)) {
                        val pckg = SLinkOperations.getTarget(it, LINKS.dataType).model?.module?.moduleName ?: ""
                        EnumPropertyType(pckg, SPropertyOperations.getString(SLinkOperations.getTarget(it, LINKS.dataType), PROPS.name))
                    } else {
                        PrimitivePropertyType(Primitive.STRING)
                    }
                    PropertyData(MetaIdByDeclaration.getPropId(it).toString(), SPropertyOperations.getString(it, PROPS.name), type, true, deprecationMsg(it))
                }.toList()
            val childLinks = SLinkOperations.getChildren(concept, LINKS.linkDeclaration)
                .filter { SLinkOperations.getTarget(it, LINKS.specializedLink) == null }
                .filter { SEnumOperations.isMember(SPropertyOperations.getEnum(it, PROPS.metaClass), 0xfc6f4e95b9L) }
                .map<SNode, ChildLinkData> {
                    exportLanguage(SLinkOperations.getTarget(it, LINKS.target))
                    ChildLinkData(
                        MetaIdByDeclaration.getLinkId(it).toString(),
                        SPropertyOperations.getString(it, PROPS.name),
                        linkTargetFqName(it),
                        !(LinkDeclaration__BehaviorDescriptor.isSingular_idhEwIfAt.invoke(it) as Boolean),
                        !(LinkDeclaration__BehaviorDescriptor.isAtLeastOneCardinality_id2VYdUfnkjmB.invoke(it) as Boolean),
                        deprecationMsg(it),
                    )
                }
                .toList()
            val referenceLinks = SLinkOperations.getChildren(concept, LINKS.linkDeclaration)
                .filter { SLinkOperations.getTarget(it, LINKS.specializedLink) == null }
                .filter { SEnumOperations.isMember(SPropertyOperations.getEnum(it, PROPS.metaClass), 0xfc6f4e95b8L) }
                .map<SNode, ReferenceLinkData> {
                    exportLanguage(SLinkOperations.getTarget(it, LINKS.target))
                    ReferenceLinkData(
                        MetaIdByDeclaration.getLinkId(it).toString(),
                        SPropertyOperations.getString(it, PROPS.name),
                        linkTargetFqName(it),
                        !(LinkDeclaration__BehaviorDescriptor.isAtLeastOneCardinality_id2VYdUfnkjmB.invoke(it) as Boolean),
                        deprecationMsg(it),
                    )
                }.toList()
            val is_abstract =
                SPropertyOperations.getBoolean(concept, PROPS.abstract) || SNodeOperations.isInstanceOf(concept, CONCEPTS.InterfaceConceptDeclaration)
            val superConcepts: List<String> = AbstractConceptDeclaration__BehaviorDescriptor.getImmediateSuperconcepts_idhMuxyK2.invoke(concept)
                .distinct()
                .filterNotNull()
                .map { it: SNode ->
                    val superLanguage = SNodeOperations.getModel(it).module as? Language
                    if (superLanguage != null) {
                        exportLanguage(superLanguage)
                    }
                    fqName(it)
                }
            val smodelAttributes = SNodeOperations
                .getChildren(concept, LINKS.smodelAttribute)
                .map(::exportAnnotation)
            val metaProperties: MutableMap<String, String> = HashMap()
            if (SPropertyOperations.getString(concept, PROPS.conceptAlias) != null) {
                metaProperties[ConceptData.ALIAS_KEY] =
                    SPropertyOperations.getString(concept, PROPS.conceptAlias)
            }
            ConceptData(
                "mps:" + MetaIdByDeclaration.getConceptId(concept).toString(),
                SPropertyOperations.getString(concept, PROPS.name),
                is_abstract,
                properties,
                childLinks,
                referenceLinks,
                superConcepts,
                deprecationMsg(concept),
                smodelAttributes,
                metaProperties,
            )
        }

        val enums = SNodeOperations.ofConcept(rootNodes, CONCEPTS.EnumerationDeclaration)
            .map<SNode, EnumData> {
                val members = SLinkOperations.getChildren(it, LINKS.members)
                    .map<SNode, EnumMemberData> {
                        val presentation =
                            if (SPropertyOperations.getString(it, PROPS.name) == SPropertyOperations.getString(it, PROPS.presentation)) {
                                null
                            } else {
                                SPropertyOperations.getString(it, PROPS.presentation)
                            }
                        EnumMemberData(
                            JavaFriendlyBase64().toString(SPropertyOperations.getString(it, PROPS.memberId).toLong()),
                            SPropertyOperations.getString(it, PROPS.name),
                            presentation,
                        )
                    }.toList()
                val defaultIndex = if (SLinkOperations.getTarget(it, LINKS.defaultMember) != null) {
                    SNodeOperations.getIndexInParent(SLinkOperations.getTarget(it, LINKS.defaultMember))
                } else {
                    0
                }
                EnumData(
                    "mps:" + MetaIdByDeclaration.getDatatypeId(it).toString(),
                    SPropertyOperations.getString(it, PROPS.name),
                    members,
                    defaultIndex,
                    deprecationMsg(it),
                )
            }.toList()

        val languageData = LanguageData(
            MetaIdByDeclaration.getLanguageId(languageModule).toString(),
            languageModule.moduleName ?: "",
            concepts,
            enums,
        )
        producedData[languageModule] = languageData
        val jsonFile = File(outputFolder, languageData.name + ".json")
        jsonFile.writeText(languageData.toJson(), StandardCharsets.UTF_8)
    }

    fun exportAnnotation(node: SNode): AnnotationData =
        exportNode(node).let {
            val annotationConcept = SNodeOperations.getConcept(node)
            AnnotationData(
                uid = MetaIdByDeclaration.getLinkId(node).toString(),
                type = fqName(annotationConcept),
                children = it.children,
                properties = it.properties,
                references = it.references,
            )
        }

    val output: List<LanguageData>
        get() = producedData.values.toList()

    fun linkTargetFqName(link: SNode?): String {
        var target = SLinkOperations.getTarget(link, LINKS.target)
        if (target == null) {
            println(
                buildString {
                    append("Link ")
                    append(SPropertyOperations.getString(SNodeOperations.`as`(SNodeOperations.getParent(link), CONCEPTS.INamedConcept), PROPS.name))
                    append(".")
                    append(SPropertyOperations.getString(link, PROPS.name))
                    append(" has no target concept. Using BaseConcept instead.")
                },
            )
            target = SPointerOperations.resolveNode(
                SNodePointer("r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)", "1133920641626"),
                SNodeOperations.getModel(link).module.repository,
            )
        }
        return fqName(target)
    }

    private fun fqName(concept: SConcept?): String {
        return concept?.let { concept.language.sourceModule?.moduleName + "." + concept.name } ?: "unknown"
    }

    private fun fqName(element: SNode?): String {
        return SNodeOperations.getModel(element).module.moduleName + "." + SPropertyOperations.getString(element, PROPS.name)
    }

    private fun deprecationMsg(node: SNode): String? {
        if (!(IDeprecatable__BehaviorDescriptor.isDeprecated_idhOwoPtR.invoke(node) as Boolean)) {
            return null
        }
        val msg: String? = IDeprecatable__BehaviorDescriptor.getMessage_idhP43_8K.invoke(node)
        return msg ?: ""
    }

    private object CONCEPTS {

        val AbstractConceptDeclaration: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration",
        )

        val EnumerationDeclaration: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            "jetbrains.mps.lang.structure.structure.EnumerationDeclaration",
        )

        val InterfaceConceptDeclaration: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103556dcafL,
            "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration",
        )

        val AttributeInfoConceptDeclaration: SConcept = MetaAdapterFactory.getConcept(
            -4094437568663370681,
            -8968368868337559369,
            2992811758677295509,
            "jetbrains.mps.lang.structure.structure.AttributeInfo",
        )

        val INamedConcept: SInterfaceConcept = MetaAdapterFactory.getInterfaceConcept(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            "jetbrains.mps.lang.core.structure.INamedConcept",
        )
    }

    private object LINKS {

        val propertyDeclaration: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0xf979c3ba6cL,
            "propertyDeclaration",
        )

        val dataType: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086bL,
            0xfc26f42fe5L,
            "dataType",
        )

        val linkDeclaration: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0xf979c3ba6bL,
            "linkDeclaration",
        )

        val specializedLink: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf98051c244L,
            "specializedLink",
        )

        val target: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf98055fef0L,
            "target",
        )

        val members: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            0x2e770ca32c607cc1L,
            "members",
        )

        val defaultMember: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            0xeeb344f63fe016cL,
            "defaultMember",
        )
        val smodelAttribute: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x10802efe25aL,
            0x47bf8397520e5942L,
            "smodelAttribute",
        )
    }

    private object PROPS {

        val name: SProperty = MetaAdapterFactory.getProperty(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            0x110396ec041L,
            "name",
        )

        val metaClass: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf980556927L,
            "metaClass",
        )

        val abstract: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0x403a32c5772c7ec2L,
            "abstract",
        )

        val conceptAlias: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0x46ab0ad5826c74caL,
            "conceptAlias",
        )

        val presentation: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c60L,
            0x9538e3a78561888L,
            "presentation",
        )

        val memberId: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c60L,
            0x13b8f6fdce540e38L,
            "memberId",
        )
        val attributeInfoRole: SProperty = MetaAdapterFactory.getProperty(
            -4094437568663370681,
            -8968368868337559369,
            2992811758677295509,
            7588428831955550663,
            "role",
        )
    }
}
