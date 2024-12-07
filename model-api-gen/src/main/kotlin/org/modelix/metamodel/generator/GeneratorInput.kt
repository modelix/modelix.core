package org.modelix.metamodel.generator

import org.modelix.model.api.IConcept
import org.modelix.model.data.ConceptData
import org.modelix.model.data.EnumData
import org.modelix.model.data.LanguageData
import org.modelix.model.data.PropertyType
import kotlin.reflect.KClass

val reservedPropertyNames: Set<String> = setOf(
    "constructor", // already exists on JS objects
    "_node", // exists in TypedNode in ts-model-api
) + IConcept::class.members.map { it.name }

interface IProcessedLanguageSet
fun LanguageSet.process(): IProcessedLanguageSet = ProcessedLanguageSet(getLanguages().map { it.language })

internal class ProcessedLanguageSet(dataList: List<LanguageData>) : IProcessedLanguageSet {

    private val languages: MutableList<ProcessedLanguage> = ArrayList()

    private lateinit var fqName2language: Map<String, ProcessedLanguage>
    private lateinit var uid2language: Map<String, ProcessedLanguage>
    private lateinit var fqName2concept: Map<String, ProcessedConcept>
    private lateinit var uid2concept: Map<String, ProcessedConcept>
    private lateinit var conceptMetaProperties: MutableSet<String>

    init {
        load(dataList)
    }

    fun addLanguage(language: ProcessedLanguage) {
        languages.add(language)
        language.languageSet = this
    }

    fun load(dataList: List<LanguageData>) {
        for (data in dataList) {
            addLanguage(
                ProcessedLanguage(data.name, data.uid).also { lang ->
                    lang.load(data.concepts)
                    lang.loadEnums(data.enums)
                },
            )
        }
        process()
    }

    private fun process() {
        initIndexes()
        resolveConceptReferences()
        collectConceptMetaProperties()
        fixRoleConflicts()
    }

    private fun collectConceptMetaProperties() {
        conceptMetaProperties = mutableSetOf()
        val concepts = languages.flatMap { it.getConcepts() }
        val keys = concepts.flatMap { it.metaProperties.keys }.toSet()
        conceptMetaProperties.addAll(keys)
    }

    private fun initIndexes() {
        fqName2language = languages.associateBy { it.name }
        uid2language = languages.filter { it.uid != null }.associateBy { it.uid!! }
        fqName2concept = languages.flatMap { it.getConcepts() }.associateBy { it.fqName() }
        uid2concept = languages.flatMap { it.getConcepts() }.filter { it.uid != null }.associateBy { it.uid!! }
        languages.forEach { lang -> lang.simpleName2concept = lang.getConcepts().associateBy { it.name } }
    }

    private fun resolveConceptReferences() {
        val visitor: (ProcessedConceptReference, ProcessedLanguage) -> Unit = { ref, contextLanguage ->
            ref.resolved = uid2concept[ref.name]
                ?: fqName2concept[ref.name]
                ?: contextLanguage.simpleName2concept[ref.name]
                ?: throw RuntimeException("Failed to resolve concept '${ref.name}'")
        }
        languages.forEach { lang -> lang.visitConceptReferences { visitor(it, lang) } }
    }

    private fun fixRoleConflicts() {
        val allConcepts = languages.asSequence().flatMap { it.getConcepts() }

        // add type suffix if there are two roles of the same name, but different type
        allConcepts.forEach { concept ->
            val conflicts: List<List<ProcessedRole>> = concept.getOwnRoles().groupBy { it.generatedName }.values.filter { it.size > 1 }
            for (conflict in conflicts) {
                val conflictsByType: Map<KClass<out ProcessedRole>, List<ProcessedRole>> = conflict.groupBy { it::class }
                conflictsByType.entries.forEach { conflictByType ->
                    conflictByType.value.forEach {
                        it.generatedName += getTypeSuffix(it)
                    }
                }
            }
        }

        // add number suffix if there are still two roles of the same name
        allConcepts.forEach { concept ->
            val conflicts: List<List<ProcessedRole>> = concept.getOwnRoles().groupBy { it.generatedName }.values.filter { it.size > 1 }
            for (conflict in conflicts) {
                conflict.forEachIndexed { index, role -> role.generatedName += "_$index" }
            }
        }

        // add concept name suffix if there is a role with the same name in a super concept
        val sameInHierarchyConflicts = HashSet<ProcessedRole>()
        allConcepts.forEach { concept ->
            concept.getAllSuperConceptsAndSelf()
                .flatMap { it.getOwnRoles() }
                .groupBy { it.generatedName }
                .values.asSequence()
                .filter { it.size > 1 }
                .forEach { sameInHierarchyConflicts.addAll(it) }
        }
        sameInHierarchyConflicts.forEach { it.generatedName += "_" + it.concept.name }

        // replace illegal names
        val illegalNames = reservedPropertyNames + conceptMetaProperties
        allConcepts.flatMap { it.getOwnRoles() }.forEach {
            if (illegalNames.contains(it.generatedName)) {
                it.generatedName += getTypeSuffix(it)
            }
        }
    }

    private fun getTypeSuffix(role: ProcessedRole): String {
        return when (role) {
            is ProcessedProperty -> "_property"
            is ProcessedReferenceLink -> "_reference"
            is ProcessedChildLink -> if (role.multiple) "_children" else "_child"
        }
    }

    fun getLanguages(): List<ProcessedLanguage> {
        return languages
    }

    fun getConceptMetaProperties() = conceptMetaProperties
}

internal class ProcessedLanguage(var name: String, var uid: String?) {
    lateinit var languageSet: ProcessedLanguageSet
    private val concepts: MutableList<ProcessedConcept> = ArrayList()
    private val enums: MutableList<ProcessedEnum> = ArrayList()
    lateinit var simpleName2concept: Map<String, ProcessedConcept>

    fun addConcept(concept: ProcessedConcept) {
        concepts.add(concept)
        concept.language = this
    }

    fun getConcepts(): List<ProcessedConcept> = concepts

    fun addEnum(enum: ProcessedEnum) {
        enums.add(enum)
        enum.language = this
    }

    fun getEnums(): List<ProcessedEnum> = enums

    fun load(dataList: List<ConceptData>) {
        for (data in dataList) {
            addConcept(
                ProcessedConcept(
                    data.name,
                    data.uid,
                    data.abstract,
                    data.extends.map { ProcessedConceptReference(it) }.toMutableList(),
                    data.deprecationMessage,
                    data.metaProperties,
                ).also { concept ->
                    concept.loadRoles(data)
                },
            )
        }
    }

    fun loadEnums(dataList: List<EnumData>) {
        for (data in dataList) {
            val enum = ProcessedEnum(data.name, data.uid, maxOf(0, data.defaultIndex), data.deprecationMessage)
            for (memberData in data.members) {
                val member = ProcessedEnumMember(memberData.name, memberData.uid, memberData.presentation)
                enum.addMember(member)
            }
            addEnum(enum)
        }
    }

    fun visitConceptReferences(visitor: (ProcessedConceptReference) -> Unit) {
        concepts.forEach { it.visitConceptReferences(visitor) }
    }
}

internal class ProcessedConceptReference(var name: String) {
    lateinit var resolved: ProcessedConcept
}

internal sealed interface IProcessedDeprecatable {
    var deprecationMessage: String?
}

internal class ProcessedEnum(
    var name: String,
    var uid: String?,
    var defaultIndex: Int,
    override var deprecationMessage: String?,
) : IProcessedDeprecatable {
    lateinit var language: ProcessedLanguage
    private val members: MutableList<ProcessedEnumMember> = ArrayList()

    fun getAllMembers() = members

    fun addMember(member: ProcessedEnumMember) {
        members.add(member)
        member.enum = this
    }
}

internal class ProcessedEnumMember(var name: String, var uid: String, var presentation: String?) {
    lateinit var enum: ProcessedEnum
}

internal class ProcessedConcept(
    var name: String,
    var uid: String?,
    var abstract: Boolean,
    val extends: MutableList<ProcessedConceptReference>,
    override var deprecationMessage: String?,
    val metaProperties: MutableMap<String, String>,
) : IProcessedDeprecatable {
    lateinit var language: ProcessedLanguage
    private val roles: MutableList<ProcessedRole> = ArrayList()

    fun fqName() = language.name + "." + name

    fun addRole(role: ProcessedRole) {
        roles.add(role)
        role.concept = this
    }

    fun getOwnRoles(): List<ProcessedRole> = roles

    fun getOwnAndDuplicateRoles(): List<ProcessedRole> = roles + getDuplicateSuperConcepts().flatMap { it.getOwnRoles() }

    fun loadRoles(data: ConceptData) {
        data.properties.forEach { addRole(ProcessedProperty(it.name, it.uid, it.optional, it.type, it.deprecationMessage)) }
        data.children.forEach { addRole(ProcessedChildLink(it.name, it.uid, it.optional, it.multiple, ProcessedConceptReference(it.type), it.deprecationMessage)) }
        data.references.forEach { addRole(ProcessedReferenceLink(it.name, it.uid, it.optional, ProcessedConceptReference(it.type), it.deprecationMessage)) }
    }

    fun visitConceptReferences(visitor: (ProcessedConceptReference) -> Unit) {
        extends.forEach { visitor(it) }
        roles.forEach { it.visitConceptReferences(visitor) }
    }

    fun getDirectSuperConcepts(): Sequence<ProcessedConcept> = extends.asSequence().map { it.resolved }
    private fun getAllSuperConcepts_(): Sequence<ProcessedConcept> = getDirectSuperConcepts().flatMap { it.getAllSuperConceptsAndSelf_() }
    private fun getAllSuperConceptsAndSelf_(): Sequence<ProcessedConcept> = sequenceOf(this) + getAllSuperConcepts_()

    fun getAllSuperConcepts(): Sequence<ProcessedConcept> = getAllSuperConcepts_().distinct()
    fun getAllSuperConceptsAndSelf(): Sequence<ProcessedConcept> = getAllSuperConceptsAndSelf_().distinct()
    fun getDuplicateSuperConcepts() = getAllSuperConcepts_().groupBy { it }.filter { it.value.size > 1 }.map { it.key }
}

internal sealed class ProcessedRole(
    var originalName: String,
    var uid: String?,
    var optional: Boolean,
    override var deprecationMessage: String?,
) : IProcessedDeprecatable {
    lateinit var concept: ProcessedConcept
    var generatedName: String = originalName

    fun setterName() = "set" + generatedName.take(1).uppercase() + generatedName.drop(1)

    abstract fun visitConceptReferences(visitor: (ProcessedConceptReference) -> Unit)
}

internal class ProcessedProperty(name: String, uid: String?, optional: Boolean, var type: PropertyType, deprecationMessage: String?) :
    ProcessedRole(name, uid, optional, deprecationMessage) {
    override fun visitConceptReferences(visitor: (ProcessedConceptReference) -> Unit) {}
}

internal sealed class ProcessedLink(name: String, uid: String?, optional: Boolean, var type: ProcessedConceptReference, deprecationMessage: String?) :
    ProcessedRole(name, uid, optional, deprecationMessage) {
    override fun visitConceptReferences(visitor: (ProcessedConceptReference) -> Unit) {
        visitor(type)
    }
}

internal class ProcessedChildLink(name: String, uid: String?, optional: Boolean, var multiple: Boolean, type: ProcessedConceptReference, deprecationMessage: String?) :
    ProcessedLink(name, uid, optional, type, deprecationMessage) {

    fun adderMethodName() = "addTo" + generatedName.take(1).uppercase() + generatedName.drop(1)
}

internal class ProcessedReferenceLink(name: String, uid: String?, optional: Boolean, type: ProcessedConceptReference, deprecationMessage: String?) :
    ProcessedLink(name, uid, optional, type, deprecationMessage)
