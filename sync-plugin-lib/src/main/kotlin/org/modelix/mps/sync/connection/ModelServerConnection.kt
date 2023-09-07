package org.modelix.mps.sync.connection

import java.net.URL

class ModelServerConnection(var baseUrl: URL) {

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
