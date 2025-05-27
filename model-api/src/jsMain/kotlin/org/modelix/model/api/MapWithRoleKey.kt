package org.modelix.model.api

@JsExport
sealed class MapWithRoleKey<V : Any>(private val type: IRoleReferenceFactory<*>) {
    private val entries = ArrayList<Pair<IRoleReference, V>>()

    private fun get(role: IRoleReference): V? {
        val index = entries.indexOfFirst { it.first.matches(role) }
        if (index < 0) return null

        // Always try to store ID and name
        val mergedRole = entries[index].first.merge(role)
        if (mergedRole !== entries[index].first) entries[index] = entries[index].copy(first = mergedRole)

        return entries[index].second
    }

    private fun put(role: IRoleReference, value: V) {
        val index = entries.indexOfFirst { it.first.matches(role) }
        if (index >= 0) {
            entries[index] = role.merge(entries[index].first) to value
        } else {
            entries.add(role to value)
        }
    }

    fun put(roleString: String, value: V) {
        put(type.fromString(roleString), value)
    }

    fun get(roleString: String): V? {
        return get(type.fromString(roleString))
    }

    fun getOrPut(roleString: String, initializer: () -> V): V {
        val role = type.fromString(roleString)
        return get(role) ?: initializer().also { put(role.merge(role), it) }
    }
}

@JsExport
class MapWithChildRoleKey<V : Any> : MapWithRoleKey<V>(IChildLinkReference)

@JsExport
class MapWithPropertyRoleKey<V : Any> : MapWithRoleKey<V>(IPropertyReference)

@JsExport
class MapWithReferenceRoleKey<V : Any> : MapWithRoleKey<V>(IReferenceLinkReference)
