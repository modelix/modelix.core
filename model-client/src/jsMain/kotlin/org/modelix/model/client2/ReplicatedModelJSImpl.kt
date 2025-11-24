@file:OptIn(DelicateCoroutinesApi::class)

package org.modelix.model.client2

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.datetime.toJSDate
import kotlin.js.Promise

internal class ReplicatedModelJSImpl(private val models: List<ReplicatedModel>) : ReplicatedModelJS {

    private val mutableModelTree = MutableModelTreeJsImpl(models.map { it.getVersionedModelTree() })

    override fun dispose() {
        // TODO The models are passed to the constructor, so this class shouldn't be responsible for their lifecycle.
        models.forEach { it.dispose() }
    }

    override fun getBranch(): MutableModelTreeJs {
        return mutableModelTree
    }

    override fun getCurrentVersionInformation(): Promise<VersionInformationJS> {
        return GlobalScope.promise {
            models.first().getCurrentVersionInformation()
        }
    }

    override fun getCurrentVersionInformations(): Promise<Array<VersionInformationJS>> {
        return GlobalScope.promise {
            models.map { it.getCurrentVersionInformation() }.toTypedArray()
        }
    }

    private fun ReplicatedModel.getCurrentVersionInformation(): VersionInformationJS {
        val currentVersion = getCurrentVersion()
        val currentVersionAuthor = currentVersion.author
        val currentVersionTime = currentVersion.getTimestamp()?.toJSDate()
        return VersionInformationJS(currentVersionAuthor, currentVersionTime, currentVersion.getContentHash())
    }
}
