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
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.NameConfig
import org.modelix.metamodel.generator.TypescriptMMGenerator
import org.modelix.metamodel.generator.processLanguageData
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
        val languagesData = loadLanguageDataFromExportedLanguages(exportedLanguagesDir.get().asFile)

        val processedLanguages = processLanguageData(
            languagesData,
            this.includedNamespaces.get(),
            this.includedLanguages.get(),
            this.includedConcepts.get(),
        )

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
