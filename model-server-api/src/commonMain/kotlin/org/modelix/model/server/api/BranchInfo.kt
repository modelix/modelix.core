package org.modelix.model.server.api

import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class BranchInfo(val id: String, val versionHash: String)
