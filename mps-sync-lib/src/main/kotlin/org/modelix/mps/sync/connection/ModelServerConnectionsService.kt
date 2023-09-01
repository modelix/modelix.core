package org.modelix.mps.sync.connection

import java.net.URL

object ModelServerConnectionsService {

    // The list of individual connections to model-servers
    private var modelServers: ArrayList<ModelServerConnection> = ArrayList<ModelServerConnection>()
        set(value) {
            field = value
        }

    // internal helper function to test contains
    private fun ArrayList<ModelServerConnection>.contains(serverURL: URL): Boolean {
        return this.indexOfFirst { serverURL == it.baseUrl } > 0
    }

    // internal helper function to get a connection based on a serverURL
    private fun ArrayList<ModelServerConnection>.get(serverURL: URL): ModelServerConnection {
        return this.first { serverURL == it.baseUrl }
    }

    @Throws(IllegalStateException::class)
    fun addModelServer(serverURL: URL): ModelServerConnection {
        if (modelServers.contains(serverURL)) {
            throw IllegalStateException("Model server repository with URL $serverURL is already present")
        } else {
            modelServers.add(ModelServerConnection(serverURL))

            // todo: update listeners on repositoryChanges here

            return modelServers.get(serverURL)
        }
    }
}
