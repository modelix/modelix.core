package org.modelix.model.client2

import kotlin.js.Date

/**
 * Contains a subset of version data like in [CPVersion].
 *
 * The full version data of an [CPVersion] is not exposed because most parts model API are not exposed to JS yet.
 * See https://issues.modelix.org/issue/MODELIX-962
 */
@JsExport
data class VersionInformationJS(
    /**
     * Author of the version.
     */
    val author: String?,
    /**
     * Creation time of the version.
     */
    val time: Date?,

    /**
     * hash string of the version
     */
    val versionHash: String?,
)
