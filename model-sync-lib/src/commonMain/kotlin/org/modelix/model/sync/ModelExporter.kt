package org.modelix.model.sync

import org.modelix.model.api.INode
import org.modelix.model.api.serialize
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.associateWithNotNull
import java.io.File

class ModelExporter(private val root: INode) {

    fun export(outputFile: File) {
        val modelData = ModelData(root = root.asExported())
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
            getReferenceTarget(it)?.getPropertyValue(idKey) ?: getReferenceTargetRef(it)?.serialize()
        },
        children = allChildren.map { it.asExported() }
    )
}