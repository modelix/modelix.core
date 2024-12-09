package org.modelix.model.area

import org.modelix.model.api.INodeResolutionScope

@Deprecated("use INodeResolutionScope")
object ContextArea {

    @Deprecated("use INodeResolutionScope.getCurrentScope()")
    fun getArea(): IArea? {
        val scopes = INodeResolutionScope.getCurrentScopes().filterIsInstance<IArea>()
        return when (scopes.size) {
            0 -> null
            1 -> scopes.single()
            else -> CompositeArea(scopes)
        }
    }

    @Deprecated("use INodeResolutionScope.runWithAdditional() or area.runWithAdditional")
    fun <T> withAdditionalContext(area: IArea, runnable: () -> T): T {
        return INodeResolutionScope.runWithAdditionalScope(area, runnable)
    }

    @Deprecated("use INodeResolutionScope.offer")
    fun <T> offer(area: IArea, r: () -> T): T {
        val current = getArea()
        return if (current == null || !current.collectAreas().contains(area)) {
            area.runWith(r)
        } else {
            r()
        }
    }
}
