package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.modelix.metamodel.*
import org.modelix.model.api.*
import java.nio.file.Path
import kotlin.reflect.KClass

private val reservedPropertyNames: Set<String> = setOf(
    "constructor", // already exists on JS objects
) + IConcept::class.members.map { it.name }

class MetaModelGenerator(val outputDir: Path) {

    private fun FileSpec.write() {
        writeTo(outputDir)
    }

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

    fun generateRegistrationHelper(classFqName: String, languages: LanguageSet) {
        val typeName = ClassName(classFqName.substringBeforeLast("."), classFqName.substringAfterLast("."))
        val cls = TypeSpec.objectBuilder(typeName)
            .addProperty(PropertySpec.builder("languages", List::class.parameterizedBy(GeneratedLanguage::class))
                .initializer("listOf(" + languages.getLanguages().map { it.language.generatedClassName() }.joinToString(", ") { it.canonicalName } + ")")
                .build())
            .build()

        FileSpec.builder(typeName.packageName, typeName.simpleName)
            .addType(cls)
            .build()
            .write()
    }

    fun generate(languages: LanguageSet) {
        for (language in languages.getLanguages()) {
            language.language.packageDir().toFile().listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            val builder = FileSpec.builder(language.language.generatedClassName().packageName, language.language.generatedClassName().simpleName)
            val file = builder.addType(generateLanguage(language)).build()
            for (concept in language.getConceptsInLanguage()) {
                generateConceptFile(concept)
            }
            file.write()
        }
    }

    private fun generateLanguage(language: LanguageSet.LanguageInSet): TypeSpec {
        val builder = TypeSpec.objectBuilder(language.language.generatedClassName())
        val conceptNamesList = language.getConceptsInLanguage()
            .joinToString(", ") { it.simpleName }
        builder.addFunction(FunSpec.builder("getConcepts")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return listOf($conceptNamesList)")
            .build())
        builder.superclass(GeneratedLanguage::class)
        builder.addSuperclassConstructorParameter("\"${language.name}\"")
        for (concept in language.getConceptsInLanguage()) {
            builder.addProperty(PropertySpec.builder(concept.simpleName, ClassName(language.name, concept.concept.conceptObjectName()))
                .initializer(language.name + "." + concept.concept.conceptObjectName())
                .build())
        }
        return builder.build()
    }

    private fun generateConceptFile(concept: LanguageSet.ConceptInLanguage) {
        FileSpec.builder(concept.language.name, concept.concept.name)
            .addType(generateConceptObject(concept))
            .addType(generateConceptWrapperInterface(concept))
            .addType(generateConceptWrapperImpl(concept))
            .addType(generateNodeWrapperInterface(concept))
            .addType(generateNodeWrapperImpl(concept))
            .addImport(PropertyAccessor::class.asClassName().packageName, PropertyAccessor::class.asClassName().simpleName)
            .addImport(ReferenceAccessor::class.asClassName().packageName, ReferenceAccessor::class.asClassName().simpleName)
            .build().write()
    }

    private fun generateConceptObject(concept: LanguageSet.ConceptInLanguage): TypeSpec {
        return TypeSpec.objectBuilder(concept.conceptObjectName()).apply {
            superclass(GeneratedConcept::class.asTypeName().parameterizedBy(
                concept.nodeWrapperImplType(),
                concept.conceptWrapperImplType()
            ))
            addSuperclassConstructorParameter("%S", concept.concept.name)
            addSuperclassConstructorParameter(concept.concept.abstract.toString())
            val instanceClassType = KClass::class.asClassName().parameterizedBy(concept.nodeWrapperImplType())
            addProperty(PropertySpec.builder(GeneratedConcept<*, *>::instanceClass.name, instanceClassType, KModifier.OVERRIDE)
                .initializer(concept.nodeWrapperImplName() + "::class")
                .build())
            addProperty(PropertySpec.builder(GeneratedConcept<*, *>::_typed.name, concept.conceptWrapperImplType(), KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("""return ${concept.conceptWrapperImplType().simpleName}.INSTANCE""").build())
                .build())
            addProperty(PropertySpec.builder(IConcept::language.name, ILanguage::class, KModifier.OVERRIDE)
                .initializer(concept.language.generatedClassName().simpleName)
                .build())
            addFunction(FunSpec.builder(GeneratedConcept<*, *>::wrap.name)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("node", INode::class)
                .addStatement("return ${concept.nodeWrapperImplName()}(node)")
                .build())
            concept.concept.uid?.let { uid ->
                addFunction(FunSpec.builder(GeneratedConcept<*, *>::getUID.name)
                    .addModifiers(KModifier.OVERRIDE)
                    .addStatement(CodeBlock.of("return %S", uid).toString())
                    .build())
            }
            addFunction(FunSpec.builder(GeneratedConcept<*, *>::getDirectSuperConcepts.name)
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("return listOf(${concept.concept.extends.joinToString(", ") { it.conceptObjectName() } })")
                .returns(List::class.asTypeName().parameterizedBy(IConcept::class.asTypeName()))
                .build())
            for (feature in concept.directFeatures()) {
                when (val data = feature.data) {
                    is PropertyData -> {
                        addProperty(PropertySpec.builder(feature.validName, IProperty::class)
                            .initializer("""${GeneratedConcept<*, *>::newProperty.name}("${feature.originalName}")""")
                            .build())
                    }
                    is ChildLinkData -> {
                        val methodName = if (data.multiple) "newChildListLink" else "newSingleChildLink"
                        addProperty(PropertySpec.builder(feature.validName, feature.generatedChildLinkType())
                            .initializer("""$methodName("${feature.originalName}", ${data.optional}, ${data.type.conceptObjectName()})""")
                            .build())
                    }
                    is ReferenceLinkData -> {
                        addProperty(PropertySpec.builder(feature.validName, feature.generatedReferenceLinkType())
                            .initializer("""newReferenceLink("${feature.originalName}", ${data.optional}, ${data.type.conceptObjectName()})""")
                            .build())
                    }
                }
            }
        }.build()
    }

    private fun generateConceptWrapperInterface(concept: LanguageSet.ConceptInLanguage): TypeSpec {
        return TypeSpec.interfaceBuilder(concept.conceptWrapperInterfaceType()).apply {
            addSuperinterface(ITypedConcept::class)
            for (extended in concept.extended()) {
                addSuperinterface(extended.conceptWrapperInterfaceType())
            }
            for (feature in concept.directFeatures()) {
                when (val data = feature.data) {
                    is PropertyData -> addProperty(PropertySpec.builder(feature.validName, IProperty::class).build())
                    is ChildLinkData -> addProperty(PropertySpec.builder(feature.validName, feature.generatedChildLinkType()).build())
                    is ReferenceLinkData -> addProperty(PropertySpec.builder(feature.validName, feature.generatedReferenceLinkType()).build())
                }
            }
        }.build()
    }

    private fun generateConceptWrapperImpl(concept: LanguageSet.ConceptInLanguage): TypeSpec {
        return TypeSpec.classBuilder(concept.conceptWrapperImplType()).apply {
            addModifiers(KModifier.OPEN)
            if (concept.extends().isEmpty()) {
            } else {
                superclass(concept.extends().first().conceptWrapperImplType())
                for (extended in concept.extends().drop(1)) {
                    addSuperinterface(extended.conceptWrapperInterfaceType(), CodeBlock.of(extended.conceptWrapperImplType().canonicalName + ".INSTANCE"))
                }
            }
            addSuperinterface(concept.conceptWrapperInterfaceType())

            primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PROTECTED).build())

            addProperty(PropertySpec.builder(ITypedConcept::_concept.name, IConcept::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(concept.conceptObjectName())
                .build())

            for (feature in concept.directFeaturesAndConflicts()) {
                when (val data = feature.data) {
                    is PropertyData -> {
                        addProperty(PropertySpec.builder(feature.validName, IProperty::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                    is ChildLinkData -> {
                        addProperty(PropertySpec.builder(feature.validName, feature.generatedChildLinkType())
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                    is ReferenceLinkData -> {
                        addProperty(PropertySpec.builder(feature.validName, feature.generatedReferenceLinkType())
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                }
            }

            addType(TypeSpec.companionObjectBuilder()
                .addProperty(PropertySpec.builder("INSTANCE", concept.conceptWrapperImplType())
                    .initializer(concept.conceptWrapperImplType().simpleName + "()").build())
                .build())
        }.build()
    }

    private fun generateNodeWrapperImpl(concept: LanguageSet.ConceptInLanguage): TypeSpec {
        return TypeSpec.classBuilder(concept.nodeWrapperImplType()).apply {
            addModifiers(KModifier.OPEN)
            addProperty(PropertySpec.builder(TypedNodeImpl::_concept.name, concept.conceptWrapperImplType(), KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("""return ${concept.conceptWrapperImplType().simpleName}.INSTANCE""").build())
                .build())

            if (concept.extends().size > 1) {
                // fix kotlin warning about ambiguity in case of multiple inheritance
                addFunction(FunSpec.builder(ITypedNode::unwrap.name)
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(INode::class)
                    .addStatement("return " + TypedNodeImpl::wrappedNode.name)
                    .build())
            }
            primaryConstructor(FunSpec.constructorBuilder().addParameter("_node", INode::class).build())
            if (concept.extends().isEmpty()) {
                superclass(TypedNodeImpl::class)
                addSuperclassConstructorParameter("_node")
            } else {
                superclass(concept.extends().first().nodeWrapperImplType())
                addSuperclassConstructorParameter("_node")
                for (extended in concept.extends().drop(1)) {
                    addSuperinterface(extended.nodeWrapperInterfaceType(), CodeBlock.of(extended.nodeWrapperImplType().canonicalName + "(_node)"))
                }
            }
            addSuperinterface(concept.nodeWrapperInterfaceType())
            for (feature in concept.directFeaturesAndConflicts()) {
                when (val data = feature.data) {
                    is PropertyData -> {
                        val optionalString = String::class.asTypeName().copy(nullable = true)
                        addProperty(PropertySpec.builder(feature.validName, optionalString)
                            .addModifiers(KModifier.OVERRIDE)
                            .mutable(true)
                            .delegate("""${PropertyAccessor::class.qualifiedName}(unwrap(), "${feature.originalName}")""")
                            .build())
                    }
                    is ChildLinkData -> {
                        // TODO resolve link.type and ensure it exists
                        val accessorSubclass = if (data.multiple) ChildListAccessor::class else SingleChildAccessor::class
                        val type = accessorSubclass.asClassName()
                            .parameterizedBy(
                                data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType())
                        val accessorName = accessorSubclass.qualifiedName
                        addProperty(PropertySpec.builder(feature.validName, type)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer("""$accessorName(${ITypedNode::unwrap.name}(), "${feature.originalName}", ${data.type.conceptObjectName()}, ${data.type.nodeWrapperInterfaceName()}::class)""")
                            .build())
                    }
                    is ReferenceLinkData -> {
                        addProperty(PropertySpec.builder(feature.validName, data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType().copy(nullable = true))
                            .addModifiers(KModifier.OVERRIDE)
                            .mutable(true)
                            .delegate("""${ReferenceAccessor::class.qualifiedName}(${ITypedNode::unwrap.name}(), "${feature.originalName}", ${data.type.nodeWrapperInterfaceName()}::class)""")
                            .build())
                    }
                }
            }
        }.build()
    }

    private fun generateNodeWrapperInterface(concept: LanguageSet.ConceptInLanguage): TypeSpec {
        return TypeSpec.interfaceBuilder(concept.nodeWrapperInterfaceType()).apply {
            if (concept.extends().isEmpty()) addSuperinterface(ITypedNode::class.asTypeName())
            for (extended in concept.extends()) {
                addSuperinterface(extended.nodeWrapperInterfaceType())
            }
            for (feature in concept.directFeatures()) {
                when (val data = feature.data) {
                    is PropertyData -> {
                        val optionalString = String::class.asTypeName().copy(nullable = true)
                        addProperty(PropertySpec.builder(feature.validName, optionalString)
                            .mutable(true)
                            .build())
                    }
                    is ChildLinkData -> {
                        // TODO resolve link.type and ensure it exists
                        val accessorSubclass = if (data.multiple) ChildListAccessor::class else SingleChildAccessor::class
                        val type = accessorSubclass.asClassName()
                            .parameterizedBy(
                                data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType())
                        addProperty(PropertySpec.builder(feature.validName, type)
                            .build())
                    }
                    is ReferenceLinkData -> {
                        addProperty(PropertySpec.builder(feature.validName, data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType().copy(nullable = true))
                            .mutable(true)
                            .build())
                    }
                }
            }
        }.build()
    }
}

fun ConceptRef.conceptWrapperImplType() = ClassName(languageName, conceptName.conceptWrapperImplName())
fun ConceptRef.conceptWrapperInterfaceType() = ClassName(languageName, conceptName.conceptWrapperInterfaceName())
fun ConceptRef.nodeWrapperImplType() = ClassName(languageName, conceptName.nodeWrapperImplName())
fun ConceptRef.nodeWrapperInterfaceType() = ClassName(languageName, conceptName.nodeWrapperInterfaceName())

fun String.languageClassName() = "L_" + this.replace(".", "_")
fun LanguageData.generatedClassName()  = ClassName(name, name.languageClassName())
fun LanguageSet.LanguageInSet.simpleClassName()  = this.language.generatedClassName().simpleName
private fun ConceptData.nodeWrapperInterfaceName() = name.nodeWrapperInterfaceName()
private fun String.nodeWrapperInterfaceName() = fqNamePrefix("N_")
private fun ConceptData.nodeWrapperImplName() = name.nodeWrapperImplName()
private fun String.nodeWrapperImplName() = fqNamePrefix("_N_TypedImpl_")
fun ConceptData.conceptObjectName() = name.conceptObjectName()
private fun String.conceptObjectName() = fqNamePrefix("_C_Impl_")
private fun ConceptData.conceptWrapperImplName() = name.conceptWrapperImplName()
private fun String.conceptWrapperImplName() = fqNamePrefix("_C_TypedImpl_")
private fun ConceptData.conceptWrapperInterfaceName() = name.conceptWrapperInterfaceName()
private fun String.conceptWrapperInterfaceName() = fqNamePrefix("C_")
private fun String.fqNamePrefix(prefix: String, suffix: String = ""): String {
    return if (this.contains(".")) {
        this.substringBeforeLast(".") + "." + prefix + this.substringAfterLast(".")
    } else {
        prefix + this
    } + suffix
}

fun LanguageSet.ConceptInLanguage.getConceptFqName() = language.name + "." + concept.name
fun LanguageSet.ConceptInLanguage.conceptObjectName() = concept.conceptObjectName()
fun LanguageSet.ConceptInLanguage.conceptObjectType() = ClassName(language.name, concept.conceptObjectName())
fun LanguageSet.ConceptInLanguage.nodeWrapperImplName() = concept.nodeWrapperImplName()
fun LanguageSet.ConceptInLanguage.nodeWrapperImplType() = ClassName(language.name, concept.nodeWrapperImplName())
fun LanguageSet.ConceptInLanguage.nodeWrapperInterfaceType() = ClassName(language.name, concept.nodeWrapperInterfaceName())
fun LanguageSet.ConceptInLanguage.conceptWrapperImplType() = ClassName(language.name, concept.conceptWrapperImplName())
fun LanguageSet.ConceptInLanguage.conceptWrapperInterfaceType() = ClassName(language.name, concept.conceptWrapperInterfaceName())

fun FeatureInConcept.kotlinRef() = concept.conceptObjectType().canonicalName + "." + CodeBlock.of("%N", validName)
fun FeatureInConcept.generatedChildLinkType(): TypeName {
    val childConcept = (data as ChildLinkData).type.parseConceptRef(concept.language)
    val linkClass = if (data.multiple) GeneratedChildListLink::class else GeneratedSingleChildLink::class
    return linkClass.asClassName().parameterizedBy(
        childConcept.nodeWrapperInterfaceType(), childConcept.conceptWrapperInterfaceType())
}
fun FeatureInConcept.generatedReferenceLinkType(): TypeName {
    val targetConcept = (data as ReferenceLinkData).type.parseConceptRef(concept.language)
    return GeneratedReferenceLink::class.asClassName().parameterizedBy(
        targetConcept.nodeWrapperInterfaceType(), targetConcept.conceptWrapperInterfaceType())
}