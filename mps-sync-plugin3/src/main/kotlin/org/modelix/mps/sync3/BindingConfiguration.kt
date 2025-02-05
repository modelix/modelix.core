package org.modelix.mps.sync3

data class BindingConfig(
    var localHeadRevision: String? = null,

    // synchronized modules
    var moduleIds: List<String> = listOf(),
)

data class ModelServerConfig(
    var url: String,
    var repositoryId: String,
    var branchName: String,
)
