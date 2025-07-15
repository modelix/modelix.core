package org.modelix.mps.sync3

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.smodel.Language
import org.jetbrains.mps.openapi.model.EditableSModel
import org.modelix.mps.api.ModelixMpsApi
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

fun Project.captureSnapshot(): String = captureFileContents().let { filterFiles(it) }.contentsAsString()

private fun Map<String, String>.contentsAsString(): String {
    return entries.sortedBy { it.key }.joinToString("\n\n\n") { "------ ${it.key} ------\n${it.value}" }
}

private fun filterFiles(files: Map<String, String>) = files.filter {
    val name = it.key
    if (name.startsWith(".mps/")) {
        when (name.substringAfter("/")) {
            ".gitignore", "migration.xml", "workspace.xml", "modelix.xml", "vcs.xml" -> false
            else -> true
        }
    } else if (name.contains("/source_gen") || name.contains("/classes_gen")) {
        false
    } else {
        true
    }
}

private fun Project.captureFileContents(): Map<String, String> {
    ApplicationManager.getApplication().invokeAndWait {
        ProjectHelper.fromIdeaProject(this)!!.modelAccess.runWriteAction {
            for (module in ProjectHelper.fromIdeaProject(this)!!.projectModules.flatMap {
                listOf(it) + ((it as? Language)?.generators ?: emptyList())
            }) {
                module as AbstractModule
                module.save()
                for (model in module.models.filterIsInstance<EditableSModel>()) {
                    if (model.isReadOnly) continue
                    ModelixMpsApi.forceSave(model)
                }
            }
        }
        ApplicationManager.getApplication().saveAll()
        save()
    }

    // Files sometimes don't get deleted. Ignore them if they are not listed in the modules.xml
    val visibleModules = HashSet<Path>()
    File(basePath).resolve(".mps/modules.xml").takeIf { it.isFile }?.let { readXmlFile(it) }?.visitAll {
        if (it is Element && it.tagName == "modulePath") {
            visibleModules.add(Path.of(it.getAttribute("path").replace("\$PROJECT_DIR\$", basePath!!)))
        }
    }

    val moduleEndings = setOf(MPSExtentions.DEVKIT, MPSExtentions.LANGUAGE, MPSExtentions.SOLUTION)
    val xmlEndings = moduleEndings + setOf(MPSExtentions.MODEL)

    return Path.of(this.basePath).walk()
        .filter { it.isRegularFile() }
        .filter {
            val isModuleFile = moduleEndings.contains(it.extension)
            !isModuleFile || visibleModules.contains(it)
        }
        .associate { file ->
            val name = file.absolute().relativeTo(Path.of(basePath).absolute()).pathString
            val content = file.readText().trim()

            val normalizedContent = when {
                xmlEndings.contains(name.substringAfterLast(".")) -> {
                    normalizeXmlFile(content)
                }
                else -> content
            }
            name to normalizedContent
        }
}

private fun normalizeXmlFile(content: String): String {
    val xml = readXmlFile(content.byteInputStream())
    xml.visitAll { node ->
        if (node !is Element) return@visitAll
        when (node.tagName) {
            "node" -> {
                node.childElements("property").sortByRole()
                node.childElements("ref").sortByRole()
                node.childElements("node").sortByRole()
            }
            "dev-kit" -> {
                node.childElements("exported-language").sortByAttribute("name")
            }
            "dependencies" -> {
                node.childElements("dependency").sortBy { it.textContent }
            }
            "sourceRoot" -> {
                val location = node.getAttribute("location")
                val path = node.getAttribute("path")
                if (path.isNullOrEmpty() && !location.isNullOrEmpty()) {
                    val contentPath = (node.parentNode as Element).getAttribute("contentPath")
                    node.removeAttribute("location")
                    node.setAttribute("path", "$contentPath/$location")
                }
            }
            "classes" -> {
                node.removeAttribute("path")
            }
            "language", "solution", "generator" -> {
                node.removeAttribute("generatorOutputPath")
            }
            "registry" -> {
                // metamodel may not be built yet and the names not available.
                // Ignore them as they don't have any semantic meaning.
                node.visitAll { (it as? Element)?.removeAttribute("name") }
            }
            "facets" -> {
                // facets are not synchronized yet
                node.parentNode.removeChild(node)
            }
        }
    }
    return xmlToString(xml).lineSequence().filter { it.isNotBlank() }.joinToString("\n")
}

private fun List<Element>.sortByRole() = sortByAttribute("role")
private fun List<Element>.sortByAttribute(name: String) = sortBy { it.getAttribute(name) }
private fun <T : Comparable<T>> List<Element>.sortBy(selector: (Element) -> T) {
    if (size < 2) return
    val sorted = sortedBy { selector(it) }
    for (i in (0..sorted.lastIndex - 1).reversed()) {
        sorted[i].parentNode.insertBefore(sorted[i], sorted[i + 1])
    }
}
