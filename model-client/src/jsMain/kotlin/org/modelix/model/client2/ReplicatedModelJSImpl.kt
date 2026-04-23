@file:OptIn(DelicateCoroutinesApi::class)

package org.modelix.model.client2

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.datetime.toJSDate
import org.modelix.model.IVersion
import kotlin.js.Promise

internal class ReplicatedModelJSImpl(private val models: List<IReplicatedOrReadonlyModel>) : ReplicatedModelJS {

    private val mutableModelTree = MutableModelTreeJsImpl(models.map { it.getMutableModelTree() })

    override fun dispose() {
        // TODO The models are passed to the constructor, so this class shouldn't be responsible for their lifecycle.
        models.forEach { it.dispose() }
    }

    override fun getBranch(): MutableModelTreeJs {
        return mutableModelTree
    }

    override fun getCurrentVersionInformation(): Promise<VersionInformationJS> {
        return GlobalScope.promise {
            models.first().getCurrentVersion().toVersionInformationJS()
        }
    }

    override fun getCurrentVersionInformations(): Promise<Array<VersionInformationJS>> {
        return GlobalScope.promise {
            models.map { it.getCurrentVersion().toVersionInformationJS() }.toTypedArray()
        }
    }

    private fun IVersion.toVersionInformationJS(): VersionInformationJS {
        return VersionInformationJS(getAuthor(), getTimestamp()?.toJSDate(), getContentHash(), getAttributes().toAttributeEntriesJS())
    }
}
