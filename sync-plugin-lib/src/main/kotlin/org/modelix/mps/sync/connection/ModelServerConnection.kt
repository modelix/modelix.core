package org.modelix.mps.sync.connection

import org.modelix.model.api.IBranch
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.binding.RootBinding
import java.net.URL

class ModelServerConnection(var baseUrl: URL) {

    companion object {
        val uiStateRepositoryId = RepositoryId("uistate")
    }

    fun getActiveBranch(repositoryId: RepositoryId): ActiveBranch {
        TODO("Not yet implemented")
    }

    fun isConnected(): Boolean {
        TODO("Not yet implemented")
    }

    fun getInfoBranch(): IBranch {
        TODO("Not yet implemented")
    }

    fun getRootBinding(repositoryId: RepositoryId): RootBinding {
        TODO("Not yet implemented")
    }

    fun addBinding(repositoryId: RepositoryId, binding: Any) {
        TODO("Not yet implemented")
    }

    fun getActiveBranches(): Iterable<ActiveBranch> {
        TODO()
    }

    private fun getInfo(): Any {
        // should return org.modelix.model.runtimelang.structure.ModelServerInfo
        TODO()
    }

    fun trees(): Iterable<CloudRepository> {
        val info = this.getInfo()
        return PArea(this.getInfoBranch()).executeRead {
            // We want to obtain a list within the transaction.
            // A sequence (which is lazy) would not work

            // TODO migrate if org.modelix.model.runtimelang.structure.ModelServerInfo is on the CP
            /*
            info.repositories.select({~it =>
                RepositoryId repositoryId = new RepositoryId(it.id);
                new CloudRepository(this, repositoryId);
            }).toList;
             */

            val result: Iterable<CloudRepository> = null!!
            result
        }
    }

    fun dispose() {
        TODO("Not yet implemented")
    }

    init {
        println("lolz")
        // todo: get the com.intellij.openapi.application.ApplicationManager and connect to the messageBus
//        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
//        messageBusConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
//            @Override
//            public void projectClosing(@NotNull() Project closingProject) {
//                foreach closingProjectBinding in bindings.values.selectMany({~it => it.getAllBindings(); }).ofType<ProjectBinding>.where({~it => ProjectHelper.toIdeaProject(it.getProject()) :eq: closingProject; }).toList {
//                    removeBinding(closingProjectBinding);
//                }
//            }
//        });
    }
}
