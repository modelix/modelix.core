package org.modelix.model.api

/**
 * Listener, that listens for changes on a branch.
 */
interface IBranchListener {
    /**
     * Informs the branch listener about tree changes.
     *
     * @param oldTree the original tree state
     * @param newTree the new tree state
     */
    fun treeChanged(oldTree: ITree?, newTree: ITree)
}
