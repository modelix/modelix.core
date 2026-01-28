package org.modelix.metamodel.export

import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SReference
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import java.io.File
import java.nio.charset.StandardCharsets

class MPSModelExporter(private val outputFolder: File) {
    fun exportModelWithDependencies(model: SModel) {
        val models: MutableSet<SModel> = HashSet()
        collectDependencies(model, models)
        val modules = models.mapNotNull { it.module }.distinct().toList()
        val data: NodeData = exportModules(modules, models)
        writeFiles(model.name.value, data)
    }

    fun exportModule(module: SModule) {
        val data: NodeData = exportModules(listOf(module), null)
        writeFiles(module.moduleName, data)
    }

    private fun collectDependencies(model: SModel, result: MutableSet<SModel>) {
        if (result.contains(model)) {
            return
        }
        result.add(model)
        val rootNodes: List<SNode> = model.rootNodes.toList()
        val referencedModels = rootNodes.asSequence()
            .flatMap { it: SNode? ->
                SNodeOperations.getNodeDescendants(
                    it,
                    CONCEPTS.BaseConcept,
                    false,
                    arrayOf(),
                )
            }
            .flatMap { SNodeOperations.getReferences(it) }
            .mapNotNull { it.targetNode }.mapNotNull { it: SNode -> it.model }.distinct()
            .toList()
        for (referencedModel in referencedModels) {
            collectDependencies(referencedModel, result)
        }
    }

    private fun writeFiles(name: String?, nodeData: NodeData) {
        val data = ModelData(null, nodeData)
        val jsonFile = File(outputFolder, "$name.json")
        jsonFile.writeText(data.toJson(), StandardCharsets.UTF_8)
    }

    fun exportModules(modules: List<SModule>): NodeData {
        return exportModules(modules, null)
    }

    private fun exportModules(modules: List<SModule>, modelsToInclude: Set<SModel?>?): NodeData {
        val modulesData: List<NodeData> = modules.map { it: SModule -> exportModule(it, modelsToInclude) }
        val root = NodeData("", null, null, modulesData, emptyMap(), emptyMap())
        return root
    }

    private fun exportModule(mpsModule: SModule, modelsToInclude: Set<SModel?>?): NodeData {
        val models: Iterable<SModel> = mpsModule.models
        val modelsData: List<NodeData> = models.filter { it: SModel? ->
            modelsToInclude == null || modelsToInclude.contains(it)
        }.map { it: SModel -> exportModel(it) }
        val properties: MutableMap<String, String> = LinkedHashMap()
        properties["id"] = mpsModule.moduleId.toString()
        properties["name"] = mpsModule.moduleName ?: ""
        return NodeData(
            mpsModule.moduleReference.toString(),
            "mps-module",
            "modules",
            modelsData,
            properties,
            emptyMap(),
        )
    }

    private fun exportModel(mpsModel: SModel): NodeData {
        val rootNodes: Iterable<SNode> = mpsModel.rootNodes
        val rootNodeData: List<NodeData> = rootNodes.map { exportNode(it) }
        val properties: MutableMap<String, String> = LinkedHashMap()
        properties["id"] = mpsModel.modelId.toString()
        properties["name"] = mpsModel.name.value
        val data = NodeData(mpsModel.reference.toString(), "mps-model", "models", rootNodeData, properties, emptyMap())
        return data
    }

    companion object {
        fun exportNode(node: SNode): NodeData {
            val id: String = node.reference.toString()
            val concept: String = "mps:" + MetaIdHelper.getConcept(SNodeOperations.getConcept(node)).serialize()
            val role = node.containmentLink?.name ?: "rootNodes"
            val children: List<NodeData> = SNodeOperations.getChildren(node).map { exportNode(it) }
            val properties: MutableMap<String, String> = LinkedHashMap()
            val references: MutableMap<String, String> = LinkedHashMap()
            properties["#mpsNodeId#"] = node.nodeId.toString()
            for (property: SProperty in node.properties) {
                properties[property.name] = node.getProperty(property) ?: continue
            }
            for (reference: SReference in node.references) {
                references[reference.link.name] = reference.targetNodeReference.toString()
            }
            return NodeData(id, concept, role, children, properties, references)
        }
    }

    private object CONCEPTS {
        val BaseConcept: SConcept = MetaAdapterFactory.getConcept(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x10802efe25aL,
            "jetbrains.mps.lang.core.structure.BaseConcept",
        )
    }
}
