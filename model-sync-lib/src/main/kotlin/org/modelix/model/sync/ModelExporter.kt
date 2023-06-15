package org.modelix.model.sync

import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.model.api.serialize
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.associateWithNotNull
import java.io.File

class ModelExporter(private val branch: IBranch) {

    fun export(outputFile: File) {
        lateinit var modelData: ModelData
        branch.runRead {
            val root = branch.getRootNode()
            modelData = ModelData(root = root.asExported())
        }
        outputFile.parentFile.mkdirs()
        outputFile.writeText(modelData.toJson())
    }


}

fun INode.asExported() : NodeData {
    val idKey = NodeData.idPropertyKey
    return NodeData(
        id = getPropertyValue(idKey) ?: reference.serialize(),
        concept = concept?.getUID(),
        role = roleInParent,
        properties = getPropertyRoles().associateWithNotNull { getPropertyValue(it) }.filterKeys { it != idKey },
        references = getReferenceRoles().associateWithNotNull {
            getReferenceTarget(it)?.let { node ->
                node.getPropertyValue(idKey) ?: node.reference.serialize()
            }
        },
        children = allChildren.map { it.asExported() }
    )
}