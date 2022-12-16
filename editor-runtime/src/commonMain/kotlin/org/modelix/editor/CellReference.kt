package org.modelix.editor

import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty

abstract class CellReference {

}

data class PropertyCellReference(val property: IProperty, val nodeRef: INodeReference) : CellReference()