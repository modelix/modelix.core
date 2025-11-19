package org.modelix.mps.sync3

abstract class ProjectSyncTestBase : MPSTestBase() {
    protected var lastSnapshotBeforeSync: String? = null
    protected var lastSnapshotAfterSync: String? = null
}
