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
import org.modelix.model.data.ChildLinkData
import org.modelix.model.data.ConceptData
import org.modelix.model.data.EnumData
import org.modelix.model.data.EnumMemberData
import org.modelix.model.data.EnumPropertyType
import org.modelix.model.data.LanguageData
import org.modelix.model.data.Primitive
import org.modelix.model.data.PrimitivePropertyType
import org.modelix.model.data.PropertyData
import org.modelix.model.data.PropertyType
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
        val language = as_y4kbwa_a0a3a8(
            model.module,
            Language::class.java,
        )
            ?: return
        exportLanguage(language)
    }

    fun exportLanguage(languageModule: Language) {
        if (processedLanguages.contains(languageModule)) {
            return
        }
        processedLanguages.add(languageModule)

        val structureModel = LanguageAspect.STRUCTURE[languageModule]
        val rootNodes = structureModel!!.rootNodes

        val concepts = SNodeOperations.ofConcept(rootNodes, CONCEPTS.`AbstractConceptDeclaration$KA`).map { concept: SNode ->
            val properties =
                SLinkOperations.getChildren(concept, LINKS.`propertyDeclaration$YUgg`)
                    .map { it: SNode ->
                        var type: PropertyType? = PrimitivePropertyType(Primitive.STRING)
                        if ((
                            SLinkOperations.getPointer(
                                it,
                                LINKS.`dataType$5j5Y`,
                            ) == SNodePointer(
                                "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)",
                                "1082983657062",
                            )
                            )
                        ) {
                            type = PrimitivePropertyType(Primitive.INT)
                        } else if ((
                            SLinkOperations.getPointer(
                                it,
                                LINKS.`dataType$5j5Y`,
                            ) == SNodePointer(
                                "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)",
                                "1082983657063",
                            )
                            )
                        ) {
                            type = PrimitivePropertyType(Primitive.BOOLEAN)
                        } else if (SNodeOperations.isInstanceOf(
                                SLinkOperations.getTarget(
                                    it,
                                    LINKS.`dataType$5j5Y`,
                                ),
                                CONCEPTS.`EnumerationDeclaration$hv`,
                            )
                        ) {
                            val pckg: String? = SLinkOperations.getTarget(it, LINKS.`dataType$5j5Y`).model!!
                                .module.moduleName
                            type = EnumPropertyType(
                                (pckg)!!,
                                SPropertyOperations.getString(
                                    SLinkOperations.getTarget(
                                        it,
                                        LINKS.`dataType$5j5Y`,
                                    ),
                                    PROPS.`name$MnvL`,
                                ),
                            )
                        }
                        PropertyData(
                            MetaIdByDeclaration.getPropId(it).toString(),
                            SPropertyOperations.getString(it, PROPS.`name$MnvL`),
                            (type)!!,
                            true,
                            deprecationMsg(it),
                        )
                    }.toList()
            val childLinks =
                SLinkOperations.getChildren(concept, LINKS.`linkDeclaration$YU1f`)
                    .filter { it: SNode? -> (SLinkOperations.getTarget(it, LINKS.`specializedLink$7ZCN`) == null) }
                    .filter { it: SNode? ->
                        SEnumOperations.isMember(
                            SPropertyOperations.getEnum(
                                it,
                                PROPS.`metaClass$PeKc`,
                            ),
                            0xfc6f4e95b9L,
                        )
                    }.map<SNode, ChildLinkData> { it: SNode ->
                        exportLanguage(SLinkOperations.getTarget(it, LINKS.`target$m40F`))
                        ChildLinkData(
                            MetaIdByDeclaration.getLinkId(it).toString(),
                            SPropertyOperations.getString(it, PROPS.`name$MnvL`),
                            linkTargetFqName(it),
                            !(LinkDeclaration__BehaviorDescriptor.isSingular_idhEwIfAt.invoke(it) as Boolean),
                            !(
                                LinkDeclaration__BehaviorDescriptor.isAtLeastOneCardinality_id2VYdUfnkjmB.invoke(
                                    it,
                                ) as Boolean
                                ),
                            deprecationMsg(it),
                        )
                    }.toList()
            val referenceLinks =
                SLinkOperations.getChildren(concept, LINKS.`linkDeclaration$YU1f`)
                    .filter { it: SNode? -> (SLinkOperations.getTarget(it, LINKS.`specializedLink$7ZCN`) == null) }
                    .filter { it: SNode? ->
                        SEnumOperations.isMember(
                            SPropertyOperations.getEnum(
                                it,
                                PROPS.`metaClass$PeKc`,
                            ),
                            0xfc6f4e95b8L,
                        )
                    }.map<SNode, ReferenceLinkData> { it: SNode ->
                        exportLanguage(SLinkOperations.getTarget(it, LINKS.`target$m40F`))
                        ReferenceLinkData(
                            MetaIdByDeclaration.getLinkId(it).toString(),
                            SPropertyOperations.getString(it, PROPS.`name$MnvL`),
                            linkTargetFqName(it),
                            !(
                                LinkDeclaration__BehaviorDescriptor.isAtLeastOneCardinality_id2VYdUfnkjmB.invoke(
                                    it,
                                ) as Boolean
                                ),
                            deprecationMsg(it),
                        )
                    }.toList()
            val is_abstract =
                SPropertyOperations.getBoolean(concept, PROPS.`abstract$ibpT`) || SNodeOperations.isInstanceOf(
                    concept,
                    CONCEPTS.`InterfaceConceptDeclaration$CG`,
                )
            val superConcepts: List<String> = AbstractConceptDeclaration__BehaviorDescriptor.getImmediateSuperconcepts_idhMuxyK2.invoke(concept)
                .distinct()
                .filterNotNull()
                .map { it: SNode ->
                    val superLanguage = as_y4kbwa_a0a0a0a0a0f0a0a0a6a01(
                        SNodeOperations.getModel(it).module,
                        Language::class.java,
                    )
                    if (superLanguage != null) {
                        exportLanguage(superLanguage)
                    }
                    fqName(it)
                }
            val metaProperties: MutableMap<String, String> = HashMap()
            if (SPropertyOperations.getString(concept, PROPS.`conceptAlias$OL_L`) != null) {
                metaProperties[ConceptData.ALIAS_KEY] =
                    SPropertyOperations.getString(concept, PROPS.`conceptAlias$OL_L`)
            }
            ConceptData(
                "mps:" + MetaIdByDeclaration.getConceptId(concept).toString(),
                SPropertyOperations.getString(concept, PROPS.`name$MnvL`),
                is_abstract,
                properties,
                childLinks,
                referenceLinks,
                superConcepts,
                deprecationMsg(concept),
                metaProperties,
            )
        }

        val enums = SNodeOperations.ofConcept(rootNodes, CONCEPTS.`EnumerationDeclaration$hv`)
            .map<SNode, EnumData> { it: SNode ->
                val members = SLinkOperations.getChildren(it, LINKS.`members$wmsL`)
                    .map<SNode, EnumMemberData> {
                        val presentation = (
                            if ((
                                SPropertyOperations.getString(
                                    it,
                                    PROPS.`name$MnvL`,
                                ) == SPropertyOperations.getString(it, PROPS.`presentation$BjyV`)
                                )
                            ) {
                                null
                            } else {
                                SPropertyOperations.getString(it, PROPS.`presentation$BjyV`)
                            }
                            )
                        EnumMemberData(
                            JavaFriendlyBase64().toString(
                                SPropertyOperations.getString(
                                    it,
                                    PROPS.`memberId$LVXV`,
                                ).toLong(),
                            ),
                            SPropertyOperations.getString(it, PROPS.`name$MnvL`),
                            presentation,
                        )
                    }.toList()
                val defaultIndex = (
                    if ((
                        SLinkOperations.getTarget(
                            it,
                            LINKS.`defaultMember$vlmG`,
                        ) != null
                        )
                    ) {
                        SNodeOperations.getIndexInParent(
                            SLinkOperations.getTarget(it, LINKS.`defaultMember$vlmG`),
                        )
                    } else {
                        0
                    }
                    )
                EnumData(
                    "mps:" + MetaIdByDeclaration.getDatatypeId(it).toString(),
                    SPropertyOperations.getString(it, PROPS.`name$MnvL`),
                    members,
                    defaultIndex,
                    deprecationMsg(it),
                )
            }.toList()

        val languageData = LanguageData(
            MetaIdByDeclaration.getLanguageId(languageModule).toString(),
            languageModule.moduleName!!,
            concepts,
            enums,
        )
        producedData[languageModule] = languageData
        val jsonFile = File(outputFolder, languageData.name + ".json")
        jsonFile.writeText(languageData.toJson(), StandardCharsets.UTF_8)
    }

    val output: List<LanguageData>
        get() = producedData.values.toList()

    fun linkTargetFqName(link: SNode?): String {
        var target = SLinkOperations.getTarget(link, LINKS.`target$m40F`)
        if (target == null) {
            println(
                "Link " + SPropertyOperations.getString(
                    SNodeOperations.`as`(
                        SNodeOperations.getParent(link),
                        CONCEPTS.`INamedConcept$Kd`,
                    ),
                    PROPS.`name$MnvL`,
                ) + "." + SPropertyOperations.getString(
                    link,
                    PROPS.`name$MnvL`,
                ) + " has no target concept. Using BaseConcept instead.",
            )
            target = SPointerOperations.resolveNode(
                SNodePointer(
                    "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)",
                    "1133920641626",
                ),
                SNodeOperations.getModel(link).module.repository,
            )
        }
        return fqName(target)
    }

    private fun fqName(element: SNode?): String {
        return SNodeOperations.getModel(element).module.moduleName + "." + SPropertyOperations.getString(
            element,
            PROPS.`name$MnvL`,
        )
    }

    private fun deprecationMsg(node: SNode): String? {
        if (!(IDeprecatable__BehaviorDescriptor.isDeprecated_idhOwoPtR.invoke(node) as Boolean)) {
            return null
        }
        val msg: String? = IDeprecatable__BehaviorDescriptor.getMessage_idhP43_8K.invoke(node)
        return (msg ?: "")
    }

    private object CONCEPTS {
        /*package*/
        val `AbstractConceptDeclaration$KA`: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration",
        )

        /*package*/
        val `EnumerationDeclaration$hv`: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            "jetbrains.mps.lang.structure.structure.EnumerationDeclaration",
        )

        /*package*/
        val `InterfaceConceptDeclaration$CG`: SConcept = MetaAdapterFactory.getConcept(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103556dcafL,
            "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration",
        )

        /*package*/
        val `INamedConcept$Kd`: SInterfaceConcept = MetaAdapterFactory.getInterfaceConcept(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            "jetbrains.mps.lang.core.structure.INamedConcept",
        )
    }

    private object LINKS {
        /*package*/
        val `propertyDeclaration$YUgg`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0xf979c3ba6cL,
            "propertyDeclaration",
        )

        /*package*/
        val `dataType$5j5Y`: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086bL,
            0xfc26f42fe5L,
            "dataType",
        )

        /*package*/
        val `linkDeclaration$YU1f`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0xf979c3ba6bL,
            "linkDeclaration",
        )

        /*package*/
        val `specializedLink$7ZCN`: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf98051c244L,
            "specializedLink",
        )

        /*package*/
        val `target$m40F`: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf98055fef0L,
            "target",
        )

        /*package*/
        val `members$wmsL`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            0x2e770ca32c607cc1L,
            "members",
        )

        /*package*/
        val `defaultMember$vlmG`: SReferenceLink = MetaAdapterFactory.getReferenceLink(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c5fL,
            0xeeb344f63fe016cL,
            "defaultMember",
        )
    }

    private object PROPS {
        /*package*/
        val `name$MnvL`: SProperty = MetaAdapterFactory.getProperty(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            0x110396ec041L,
            "name",
        )

        /*package*/
        val `metaClass$PeKc`: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0xf979bd086aL,
            0xf980556927L,
            "metaClass",
        )

        /*package*/
        val `abstract$ibpT`: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0x403a32c5772c7ec2L,
            "abstract",
        )

        /*package*/
        val `conceptAlias$OL_L`: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x1103553c5ffL,
            0x46ab0ad5826c74caL,
            "conceptAlias",
        )

        /*package*/
        val `presentation$BjyV`: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c60L,
            0x9538e3a78561888L,
            "presentation",
        )

        /*package*/
        val `memberId$LVXV`: SProperty = MetaAdapterFactory.getProperty(
            -0x38d25d468331bbb9L,
            -0x7c760bf823eea749L,
            0x2e770ca32c607c60L,
            0x13b8f6fdce540e38L,
            "memberId",
        )
    }

    companion object {
        private fun <T> as_y4kbwa_a0a3a8(o: Any, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T else null)
        }

        private fun <T> as_y4kbwa_a0a0a0a0a0f0a0a0a6a01(o: Any, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T else null)
        }
    }
}
