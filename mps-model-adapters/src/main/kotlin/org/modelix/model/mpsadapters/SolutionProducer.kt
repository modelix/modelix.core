@file:Suppress("removal")

package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.DevkitDescriptor
import jetbrains.mps.project.structure.modules.GeneratorDescriptor
import jetbrains.mps.project.structure.modules.LanguageDescriptor
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.vfs.IFile

class SolutionProducer(private val myProject: IMPSProject) {

    fun create(name: String, id: ModuleId): Solution {
        val basePath = checkNotNull(myProject.getBasePath()) { "Project has no base path: $myProject" }
        val projectBaseDir = myProject.getFileSystem().getFile(basePath)
        val solutionBaseDir = projectBaseDir.findChild("solutions").findChild(name)
        return create(name, id, solutionBaseDir)
    }

    fun create(namespace: String, id: ModuleId, moduleDir: IFile): Solution {
        val descriptorFile = moduleDir.findChild(namespace + MPSExtentions.DOT_SOLUTION)
        val descriptor: SolutionDescriptor = createSolutionDescriptor(namespace, id, descriptorFile)
        val module = GeneralModuleFactory().instantiate(descriptor, descriptorFile) as Solution
        myProject.addModule(module)
        module.save()
        return module
    }

    private fun createSolutionDescriptor(namespace: String, id: ModuleId, descriptorFile: IFile): SolutionDescriptor {
        val descriptor = SolutionDescriptor()
        // using outputPath instead of outputRoot for backwards compatibility
        // descriptor.outputRoot = "\${module}/source_gen"
        descriptor.outputPath = descriptorFile.parent!!.findChild("source_gen").path
        descriptor.namespace = namespace
        descriptor.id = id
        val moduleLocation = descriptorFile.parent
        val modelsDir = moduleLocation!!.findChild(Solution.SOLUTION_MODELS)
        check(!(modelsDir.exists() && modelsDir.children?.isNotEmpty() == true)) {
            "Trying to create a solution in an existing solution's directory: $moduleLocation"
        }

        modelsDir.mkdirs()

        descriptor.modelRootDescriptors.add(DefaultModelRoot.createDescriptor(modelsDir.parent!!, modelsDir))
        descriptor.outputPath = descriptorFile.parent!!.findChild("source_gen").path
        return descriptor
    }
}

class LanguageProducer(private val myProject: IMPSProject) {

    fun create(name: String, id: ModuleId): Language {
        val basePath = checkNotNull(myProject.getBasePath()) { "Project has no base path: $myProject" }
        val projectBaseDir = myProject.getFileSystem().getFile(basePath)
        val solutionBaseDir = projectBaseDir.findChild("languages").findChild(name)
        return create(name, id, solutionBaseDir)
    }

    fun create(namespace: String, id: ModuleId, moduleDir: IFile): Language {
        val descriptorFile = moduleDir.findChild(namespace + MPSExtentions.DOT_LANGUAGE)
        val descriptor = createDescriptor(namespace, id, descriptorFile)
        val module = GeneralModuleFactory().instantiate(descriptor, descriptorFile) as Language
        myProject.addModule(module)
        module.save()
        return module
    }

    private fun createDescriptor(namespace: String, id: ModuleId, descriptorFile: IFile): LanguageDescriptor {
        val descriptor = LanguageDescriptor()
        // using genPath instead of outputRoot for backwards compatibility
        // descriptor.outputRoot = "\${module}/source_gen"
        descriptor.genPath = descriptorFile.parent!!.findChild("source_gen").path
        descriptor.namespace = namespace
        descriptor.id = id
        val moduleLocation = descriptorFile.parent
        val modelsDir = moduleLocation!!.findChild(Language.LANGUAGE_MODELS)
        check(!(modelsDir.exists() && modelsDir.children?.isNotEmpty() == true)) {
            "Trying to create a language in an existing language's directory: $moduleLocation"
        }

        modelsDir.mkdirs()

        descriptor.modelRootDescriptors.add(DefaultModelRoot.createDescriptor(modelsDir.parent!!, modelsDir))
        return descriptor
    }
}

class GeneratorProducer(private val myProject: IMPSProject) {

    fun create(language: Language, name: String, id: ModuleId, alias: String?): Generator {
        val basePath = checkNotNull(myProject.getBasePath()) { "Project has no base path: $myProject" }
        val projectBaseDir = myProject.getFileSystem().getFile(basePath)
        val solutionBaseDir = projectBaseDir.findChild("languages").findChild(language.moduleName!!)
        return create(language, name, id, alias, solutionBaseDir)
    }

    fun create(language: Language, namespace: String, id: ModuleId, alias: String?, languageModuleDir: IFile): Generator {
        val siblingDirs = language.generators.mapNotNull { it.getGeneratorLocation() }.toSet()
        val generatorLocation: IFile = findEmptyGeneratorDir(languageModuleDir, siblingDirs)
        generatorLocation.mkdirs()

        val descriptor = createDescriptor(namespace, id, alias, generatorLocation, null)
        descriptor.sourceLanguage = language.moduleReference
        language.moduleDescriptor.generators.add(descriptor)
        language.setModuleDescriptor(language.moduleDescriptor) // instantiate generator module

        language.save()

        return language.generators.first { it.moduleReference.moduleId == id }
    }

    private fun findEmptyGeneratorDir(languageModuleDir: IFile, siblingDirs: Set<IFile>): IFile {
        var folderName = "generator"
        var cnt = 1
        var newChild: IFile?
        do {
            newChild = languageModuleDir.findChild(folderName)
            folderName = "generator" + cnt++
        } while (siblingDirs.contains(newChild) || newChild.exists() && (!newChild.isDirectory || !newChild.children!!.isEmpty()))
        return newChild
    }

    private fun createDescriptor(namespace: String, id: ModuleId, alias: String?, generatorModuleLocation: IFile, templateModelsLocation: IFile?): GeneratorDescriptor {
        val descriptor = GeneratorDescriptor()
        // using outputPath instead of outputRoot for backwards compatibility
        // descriptor.outputRoot = "\${module}/${generatorModuleLocation.name}/source_gen"
        descriptor.outputPath = generatorModuleLocation.findChild(AbstractModule.CLASSES_GEN).path
        descriptor.namespace = namespace
        descriptor.id = id
        descriptor.alias = alias ?: "main"
        val modelRoot = if (templateModelsLocation == null) {
            DefaultModelRoot.createDescriptor(generatorModuleLocation, generatorModuleLocation.findChild("templates"))
        } else {
            DefaultModelRoot.createSingleFolderDescriptor(templateModelsLocation)
        }
        descriptor.modelRootDescriptors.add(modelRoot)
        return descriptor
    }
}

fun Generator.getGeneratorLocation(): IFile? {
    return modelRoots.filterIsInstance<FileBasedModelRoot>().mapNotNull { it.contentDirectory }.firstOrNull()
}

class DevkitProducer(private val myProject: IMPSProject) {

    fun create(name: String, id: ModuleId): DevKit {
        val basePath = checkNotNull(myProject.getBasePath()) { "Project has no base path: $myProject" }
        val projectBaseDir = myProject.getFileSystem().getFile(basePath)
        val solutionBaseDir = projectBaseDir.findChild("devkits").findChild(name)
        return create(name, id, solutionBaseDir)
    }

    fun create(namespace: String, id: ModuleId, moduleDir: IFile): DevKit {
        val descriptorFile = moduleDir.findChild(namespace + MPSExtentions.DOT_DEVKIT)
        val descriptor = createDescriptor(namespace, id, descriptorFile)
        val module = GeneralModuleFactory().instantiate(descriptor, descriptorFile) as DevKit
        myProject.addModule(module)
        module.save()
        return module
    }

    private fun createDescriptor(namespace: String, id: ModuleId, descriptorFile: IFile): DevkitDescriptor {
        val descriptor = DevkitDescriptor()
        descriptor.namespace = namespace
        descriptor.id = id
        return descriptor
    }
}
