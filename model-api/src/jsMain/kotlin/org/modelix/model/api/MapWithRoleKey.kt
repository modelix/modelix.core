package org.modelix.model.api

import ReferenceRole

@JsExport
sealed class MapWithRoleKey<V : Any>(private val type: IRoleReferenceFactory<*>) {
    private val entries = ArrayList<Pair<IRoleReference, V>>()

    private fun _get(role: IRoleReference): V? {
        val index = entries.indexOfFirst { it.first.matches(role) }
        if (index < 0) return null

        // Always try to store ID and name
        val mergedRole = entries[index].first.merge(role)
        if (mergedRole !== entries[index].first) entries[index] = entries[index].copy(first = mergedRole)

        return entries[index].second
    }

    private fun _put(role: IRoleReference, value: V) {
        val index = entries.indexOfFirst { it.first.matches(role) }
        if (index >= 0) {
            entries[index] = role.merge(entries[index].first) to value
        } else {
            entries.add(role to value)
        }
    }

    fun put(role: ReferenceRole, value: V) {
        _put(type.fromRoleOrString(role), value)
    }

    fun get(role: ReferenceRole): V? {
        return _get(type.fromRoleOrString(role))
    }

    fun getOrPut(role: ReferenceRole, initializer: () -> V): V {
        val convertedRole = type.fromRoleOrString(role)
        return _get(convertedRole) ?: initializer().also { _put(convertedRole.merge(convertedRole), it) }
    }
}

@JsExport
class MapWithChildRoleKey<V : Any> : MapWithRoleKey<V>(IChildLinkReference)

@JsExport
class MapWithPropertyRoleKey<V : Any> : MapWithRoleKey<V>(IPropertyReference)

@JsExport
class MapWithReferenceRoleKey<V : Any> : MapWithRoleKey<V>(IReferenceLinkReference)
