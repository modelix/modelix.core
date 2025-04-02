package org.modelix.model.test

import org.modelix.model.api.INode
import org.modelix.model.api.addNewChild
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.api.getAncestors
import org.modelix.streams.IStream
import org.modelix.streams.plus
import kotlin.random.Random

class RandomModelChangeGenerator(val rootNode: INode, private val rand: Random) {
    val childRoles = listOf("cRole1", "cRole2", "cRole3")
    val propertyRoles = listOf("pRole1", "pRole2", "pRole3")
    val referenceRoles = listOf("rRole1", "rRole2", "rRole3")
    val deleteOp: () -> Unit = {
        val nodeToDelete = getRandomNode(condition = { it.allChildren.toList().isEmpty() }, includeRoot = false)
        val parent = nodeToDelete?.parent
        if (nodeToDelete != null && nodeToDelete != rootNode && parent != null) {
            // println("$parent.removeChild($nodeToDelete)")
            parent.removeChild(nodeToDelete)
        }
    }
    val addNewOp: () -> Unit = {
        val parent = getRandomNode()
        if (parent != null) {
            val role = childRoles[rand.nextInt(childRoles.size)]
            val index = if (rand.nextBoolean()) rand.nextInt(parent.getChildren(role).count() + 1) else -1
            // println("$parent.addNewChild($role, $index)")
            val child = parent.addNewChild(role, index)
            // println(" -> $child")
        }
    }
    val setPropertyOp: () -> Unit = {
        val node = getRandomNode()
        if (node != null) {
            val role = propertyRoles[rand.nextInt(propertyRoles.size)]
            val chars = "abcdABCDⓐⓑⓒⓓ"
            val value = (1..10).map { chars.elementAt(rand.nextInt(chars.length)) }.joinToString()
            // println("$node.setPropertyValue($role, $value)")
            node.setPropertyValue(role, value)
        }
    }
    val setReferenceOp: () -> Unit = {
        val source = getRandomNode()
        val target = getRandomNode()
        if (source != null && target != null) {
            val role = referenceRoles[rand.nextInt(referenceRoles.size)]
            // println("$source.setReferenceTarget($role, $target)")
            source.setReferenceTarget(role, target)
        }
    }
    val moveOp: () -> Unit = {
        val child = getRandomNode(includeRoot = false)
        val parent = getRandomNode(condition = { it.getAncestors(true).none { anc -> anc == child } })
        if (child != null && parent != null) {
            val role = childRoles[rand.nextInt(childRoles.size)]
            var index = if (rand.nextBoolean()) rand.nextInt(parent.getChildren(role).count() + 1) else -1
            // println("$parent.moveChild($role, $index, $child)")
            parent.moveChild(role, index, child)
        }
    }
    var operations: List<() -> Unit> = listOf(
        deleteOp,
        addNewOp,
        setPropertyOp,
        setReferenceOp,
        moveOp,
    )

    fun growingOperationsOnly(): RandomModelChangeGenerator {
        operations = listOf(
            addNewOp,
            setPropertyOp,
            setReferenceOp,
        )
        return this
    }

    fun withoutMove(): RandomModelChangeGenerator {
        operations = operations - moveOp
        return this
    }

    fun addOperationOnly(): RandomModelChangeGenerator {
        operations = listOf(
            addNewOp,
        )
        return this
    }

    fun applyRandomChange() {
        operations[rand.nextInt(operations.size)]()
    }

    private fun getRandomNode(condition: (INode) -> Boolean = { true }, includeRoot: Boolean = true): INode? {
        return rootNode.asAsyncNode()
            .getDescendantsShuffled(rand, includeRoot)
            .map { it.asRegularNode() }
            .filter(condition)
            .firstOrNull()
            .getSynchronous()
    }
}

fun IAsyncNode.getDescendantsShuffled(rand: Random, includeSelf: Boolean): IStream.Many<IAsyncNode> {
    return getAllChildren().toList().flatMap { children ->
        val children = children.shuffled(rand)
        val splitAt = rand.nextInt(children.size + 1)
        IStream.many(children.take(splitAt)).flatMap { it.getDescendantsShuffled(rand, true) } +
            (if (includeSelf) IStream.of(this) else IStream.empty()) +
            IStream.many(children.drop(splitAt)).flatMap { it.getDescendantsShuffled(rand, true) }
    }
}
