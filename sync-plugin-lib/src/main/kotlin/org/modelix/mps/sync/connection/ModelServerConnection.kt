package org.modelix.mps.sync.connection

import org.modelix.model.api.IBranch
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.binding.RootBinding
import java.net.URL

class ModelServerConnection(var baseUrl: URL) {
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
