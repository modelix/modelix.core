package org.modelix.metamodel

import org.modelix.model.api.INode
import kotlin.reflect.KClass

/**
 * Thrown when a node cannot be viewed through the requested typed node class.
 *
 * The usual cause is that the node's stored concept is not part of any generated language that is
 * registered in the [TypedLanguagesRegistry] (for example because the model contains data written by
 * a language the reading application doesn't generate an API for). In that case
 * [TypedLanguagesRegistry.wrapNode] falls back to an [UnknownConceptInstance], which cannot be cast
 * to the requested typed node class.
 *
 * It extends [ClassCastException] so that code which used to catch the plain cast failure keeps
 * working, while the message now names the node, its concept and the expected type.
 *
 * Use [ChildAccessor.known]/[ChildAccessor.unknown] to traverse a containment that is expected to
 * hold nodes of unknown concepts.
 */
class UnknownConceptException(
    /** Serialized reference of the node that could not be wrapped, or a placeholder if unavailable. */
    val nodeReference: String,
    /** UID of the concept stored on the node, or `null` if the node has no concept. */
    val conceptUID: String?,
    /** Fully qualified name of the typed node class the caller asked for. */
    val expectedType: String,
    /** Name of the containment link the node is stored in, if it was cheaply available. */
    val containmentRole: String?,
) : ClassCastException(
    buildMessage(nodeReference, conceptUID, expectedType, containmentRole),
) {
    companion object {
        private fun buildMessage(
            nodeReference: String,
            conceptUID: String?,
            expectedType: String,
            containmentRole: String?,
        ): String = buildString {
            append("Node ")
            append(nodeReference)
            containmentRole?.let {
                append(" (in role '")
                append(it)
                append("')")
            }
            append(" cannot be used as ")
            append(expectedType)
            append(". Its concept ")
            append(conceptUID?.let { "'$it'" } ?: "<none>")
            append(
                " is not provided by any generated language registered in the TypedLanguagesRegistry" +
                    " (or resolves to a different concept than expected). Register the language," +
                    " or use the lenient accessors known()/unknown() to skip nodes of unknown concepts.",
            )
        }

        internal fun of(node: INode, expectedType: KClass<*>): UnknownConceptException = UnknownConceptException(
            nodeReference = runCatching { node.reference.serialize() }.getOrElse { "<unknown reference>" },
            conceptUID = runCatching { node.getConceptReference()?.getUID() }.getOrNull(),
            expectedType = expectedType.simpleName ?: expectedType.toString(),
            containmentRole = runCatching { node.getContainmentLink()?.getSimpleName() }.getOrNull()
                ?: runCatching {
                    @Suppress("DEPRECATION")
                    node.roleInParent
                }.getOrNull(),
        )
    }
}
