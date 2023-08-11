package org.modelix.metamodel.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.modelix.metamodel.generator.LanguageSet
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.TypescriptMMGenerator
import org.modelix.metamodel.generator.process
import org.modelix.model.data.LanguageData
import javax.inject.Inject

@CacheableTask
abstract class GenerateMetaModelSources @Inject constructor(of: ObjectFactory) : DefaultTask() {
    @get:InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val exportedLanguagesDir: DirectoryProperty = of.directoryProperty()

    @get:OutputDirectory
    @Optional
    val kotlinOutputDir: DirectoryProperty = of.directoryProperty()

    @get:OutputDirectory
    @Optional
    val modelqlKotlinOutputDir: DirectoryProperty = of.directoryProperty()

    @get:OutputDirectory
    @Optional
    val typescriptOutputDir: DirectoryProperty = of.directoryProperty()

    @get:Input
    val includedNamespaces: ListProperty<String> = of.listProperty(String::class.java)

    @get:Input
    val includedLanguages: ListProperty<String> = of.listProperty(String::class.java)

    @get:Input
    val includedConcepts: ListProperty<String> = of.listProperty(String::class.java)

    @get:Input
    @Optional
    val registrationHelperName: Property<String> = of.property(String::class.java)

    @get: Input
    val nameConfig: Property<NameConfig> = of.property(NameConfig::class.java)

    @TaskAction
    fun generate() {
        var languages = LanguageSet(
            exportedLanguagesDir.get().asFile.walk()
                .filter { it.extension.lowercase() == "json" }
                .map { LanguageData.fromJson(it.readText()) }
                .toList(),
        )
        val previousLanguageCount = languages.getLanguages().size

        val includedNamespaces = this.includedNamespaces.get().map { it.trimEnd('.') }
        val includedLanguages = this.includedLanguages.get()
        val includedLanguagesAndNS = this.includedLanguages.get() + includedNamespaces
        val namespacePrefixes = includedNamespaces.map { it + "." }
        val includedConcepts = this.includedConcepts.get()

        languages = languages.filter {
            languages.getLanguages().filter { lang ->
                includedLanguagesAndNS.contains(lang.name) ||
                    namespacePrefixes.any { lang.name.startsWith(it) }
            }.forEach { lang ->
                lang.getConceptsInLanguage().forEach { concept ->
                    includeConcept(concept.fqName)
                }
            }
            includedConcepts.forEach { includeConcept(it) }
        }

        val missingLanguages = includedLanguages - languages.getLanguages().map { it.name }.toSet()
        val missingConcepts = includedConcepts - languages.getLanguages().flatMap { it.getConceptsInLanguage() }.map { it.fqName }.toSet()

        if (missingLanguages.isNotEmpty() || missingConcepts.isNotEmpty()) {
            throw RuntimeException("The following languages or concepts were not found: " + (missingLanguages + missingConcepts))
        }

        println("${languages.getLanguages().size} of $previousLanguageCount languages included")

        val processedLanguages = languages.process()

        val kotlinOutputDir = this.kotlinOutputDir.orNull?.asFile
        if (kotlinOutputDir != null) {
            val generator = MetaModelGenerator(
                kotlinOutputDir.toPath(),
                nameConfig.get(),
                this.modelqlKotlinOutputDir.orNull?.asFile?.toPath(),
            )
            generator.generate(processedLanguages)
            registrationHelperName.orNull?.let {
                generator.generateRegistrationHelper(it, processedLanguages)
            }
        }

        val typescriptOutputDir = this.typescriptOutputDir.orNull?.asFile
        if (typescriptOutputDir != null) {
            val tsGenerator = TypescriptMMGenerator(typescriptOutputDir.toPath(), nameConfig.get())
            tsGenerator.generate(processedLanguages)
        }
    }
}
