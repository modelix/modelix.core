package org.modelix.mps.sync.connection

import java.net.URL

object ModelServerConnectionsService {

    // The list of individual connections to model-servers
    private var modelServers = mutableMapOf<URL, ModelServerConnection>()

    @Throws(IllegalStateException::class)
    fun addModelServer(serverURL: URL): ModelServerConnection? {
        if (modelServers.contains(serverURL)) {
            throw IllegalStateException("Model server repository with URL $serverURL is already present")
        } else {
            modelServers[serverURL] = ModelServerConnection(serverURL)

            // todo: update listeners on repositoryChanges here

            return modelServers[serverURL]
        }
    }
}
