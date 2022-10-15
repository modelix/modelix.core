package org.modelix.metamodel

import org.modelix.model.api.IConcept
import kotlin.js.JsExport

@JsExport
interface ITypedConcept {
    val _concept: IConcept
}